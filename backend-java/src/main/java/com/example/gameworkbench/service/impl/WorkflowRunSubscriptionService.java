package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.dto.workflow.WorkflowRunSseEventDTO;
import com.example.gameworkbench.entity.WorkflowRunEvent;
import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.service.WorkflowRunSseEmitterFactory;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only SSE projection of persisted WorkflowRun snapshots and events. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunSubscriptionService {
    private final WorkflowRunQueryService workflowRunQueryService;
    private final WorkflowRunEventService workflowRunEventService;
    private final WorkflowRunSseEmitterFactory emitterFactory;
    private final Map<String, Map<String, Subscriber>> subscribersByRun = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, String workflowRunUuid, String lastEventId) {
        WorkflowRunDetailVO snapshot = workflowRunQueryService.getRun(userId, workflowRunUuid);
        long snapshotSequence = snapshot.getLastSequence() == null ? 0 : snapshot.getLastSequence();
        long replayAfter = validLastEventId(lastEventId, snapshotSequence) ? Long.parseLong(lastEventId) : snapshotSequence;
        SseEmitter emitter = emitterFactory.create();

        if (terminal(snapshot.getStatus())) {
            sendSnapshot(emitter, snapshot, snapshotSequence);
            replay(emitter, workflowRunUuid, replayAfter);
            emitter.complete();
            return emitter;
        }

        String subscriptionId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(workflowRunUuid, subscriptionId, emitter);
        register(subscriber);
        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> removeAndComplete(subscriber));
        emitter.onError(ignored -> remove(subscriber));
        try {
            sendSnapshot(emitter, snapshot, snapshotSequence);
            replay(emitter, workflowRunUuid, replayAfter);
            subscriber.activate();
            log.info("[WorkflowSse] subscribed workflowRunUuid={} subscriptionId={}", workflowRunUuid, subscriptionId);
        } catch (RuntimeException exception) {
            remove(subscriber);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @EventListener
    public void onPersistedEvent(WorkflowRunEvent event) {
        Map<String, Subscriber> subscribers = subscribersByRun.get(event.getWorkflowRunUuid());
        if (subscribers == null) {
            return;
        }
        new ArrayList<>(subscribers.values()).forEach(subscriber -> subscriber.offer(event));
    }

    @Scheduled(fixedDelayString = "${app.workflow-sse.heartbeat-ms:15000}")
    public void sendHeartbeats() {
        subscribersByRun.values().forEach(subscribers -> new ArrayList<>(subscribers.values())
                .forEach(subscriber -> subscriber.sendHeartbeat()));
    }

    int subscriberCount(String workflowRunUuid) {
        Map<String, Subscriber> subscribers = subscribersByRun.get(workflowRunUuid);
        return subscribers == null ? 0 : subscribers.size();
    }

    private void register(Subscriber subscriber) {
        subscribersByRun.computeIfAbsent(subscriber.workflowRunUuid, ignored -> new ConcurrentHashMap<>())
                .put(subscriber.subscriptionId, subscriber);
    }

    private void removeAndComplete(Subscriber subscriber) {
        remove(subscriber);
        subscriber.emitter.complete();
    }

    private void remove(Subscriber subscriber) {
        Map<String, Subscriber> subscribers = subscribersByRun.get(subscriber.workflowRunUuid);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(subscriber.subscriptionId);
        if (subscribers.isEmpty()) {
            subscribersByRun.remove(subscriber.workflowRunUuid, subscribers);
        }
        log.info("[WorkflowSse] unsubscribed workflowRunUuid={} subscriptionId={}",
                subscriber.workflowRunUuid, subscriber.subscriptionId);
    }

    private void sendSnapshot(SseEmitter emitter, WorkflowRunDetailVO snapshot, long sequence) {
        send(emitter, SseEmitter.event().id(String.valueOf(sequence)).name("snapshot").data(snapshot));
    }

    private void replay(SseEmitter emitter, String workflowRunUuid, long afterSequence) {
        workflowRunEventService.findAfter(workflowRunUuid, afterSequence).forEach(event -> sendIncremental(emitter, event));
    }

    private void sendIncremental(SseEmitter emitter, WorkflowRunEvent event) {
        send(emitter, SseEmitter.event().id(String.valueOf(event.getSequence())).name(event.getEventType()).data(toDto(event)));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Workflow SSE send failed", exception);
        }
    }

    private WorkflowRunSseEventDTO toDto(WorkflowRunEvent event) {
        return WorkflowRunSseEventDTO.builder().eventType(event.getEventType()).workflowRunUuid(event.getWorkflowRunUuid())
                .sequence(event.getSequence()).occurredAt(event.getOccurredAt()).stepKey(event.getStepKey())
                .status(event.getStatus()).attempt(event.getAttempt()).artifactUuid(event.getArtifactUuid()).build();
    }

    private boolean validLastEventId(String value, long snapshotSequence) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            long sequence = Long.parseLong(value);
            return sequence >= 0 && sequence <= snapshotSequence;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean terminal(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status) || "CANCELED".equals(status);
    }

    private final class Subscriber {
        private final String workflowRunUuid;
        private final String subscriptionId;
        private final SseEmitter emitter;
        private final List<WorkflowRunEvent> buffered = new ArrayList<>();
        private boolean active;

        private Subscriber(String workflowRunUuid, String subscriptionId, SseEmitter emitter) {
            this.workflowRunUuid = workflowRunUuid;
            this.subscriptionId = subscriptionId;
            this.emitter = emitter;
        }

        private synchronized void offer(WorkflowRunEvent event) {
            if (!active) {
                buffered.add(event);
                return;
            }
            sendOrRemove(event);
        }

        private synchronized void activate() {
            active = true;
            buffered.stream().sorted(Comparator.comparing(WorkflowRunEvent::getSequence)).forEach(this::sendOrRemove);
            buffered.clear();
        }

        private void sendOrRemove(WorkflowRunEvent event) {
            try {
                sendIncremental(emitter, event);
                if ("run.terminal".equals(event.getEventType())) {
                    removeAndComplete(this);
                }
            } catch (RuntimeException exception) {
                remove(this);
                emitter.completeWithError(exception);
            }
        }

        private void sendHeartbeat() {
            try {
                send(emitter, SseEmitter.event().comment("heartbeat"));
            } catch (RuntimeException exception) {
                remove(this);
                emitter.completeWithError(exception);
            }
        }
    }
}
