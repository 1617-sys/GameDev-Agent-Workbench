package com.example.gameworkbench.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gameworkbench.application.workflow.WorkflowRunner;
import com.example.gameworkbench.config.WorkflowConsumerProperties;
import com.example.gameworkbench.config.WorkflowRetryProperties;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.RedisService;
import com.rabbitmq.client.Channel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;

@ExtendWith(MockitoExtension.class)
class WorkflowMessageConsumerTest {

    @Mock private WorkflowRunMapper runs;
    @Mock private GameProjectMapper projects;
    @Mock private RedisService redis;
    @Mock private WorkflowRunner runner;
    @Mock private Channel channel;
    @Mock private WorkflowErrorClassifier classifier;
    @Mock private RabbitTemplate rabbitTemplate;
    private WorkflowMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new WorkflowMessageConsumer(runs, projects, redis, runner, new WorkflowConsumerProperties(900),
                classifier, new WorkflowRetryProperties(3, 30000, 300000, 1800000), rabbitTemplate);
    }

    @Test
    void terminalDuplicateIsAckedWithoutLockOrRunner() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("SUCCESS"));

        consumer.consume(message(), channel, 11L);

        verify(channel).basicAck(11L, false);
        verify(redis, never()).tryLock(any(), any(), any(Long.class));
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void redisLockFailureDoesNotExecuteRunnerAndIsAckedAsDuplicate() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("QUEUED"));
        when(redis.tryLock(any(), any(), any(Long.class))).thenReturn(false);

        consumer.consume(message(), channel, 12L);

        verify(channel).basicAck(12L, false);
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void mysqlClaimFailureDoesNotExecuteRunnerAndReleasesOnlyOwnedLock() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("QUEUED"));
        when(redis.tryLock(any(), any(), any(Long.class))).thenReturn(true);
        when(runs.claimForExecution(eq("run"), eq(1), any(), any())).thenReturn(0);

        consumer.consume(message(), channel, 13L);

        verify(channel).basicAck(13L, false);
        verify(runner, never()).run(any(), any(), any());
        verify(redis).releaseLock(eq("workflow:execute:run"), any());
    }

    @Test
    void runnerSuccessIsDurablyTerminalBeforeManualAck() throws Exception {
        WorkflowRun pending = run("QUEUED");
        WorkflowRun success = run("SUCCESS");
        when(runs.selectOne(any())).thenReturn(pending, success);
        when(redis.tryLock(any(), any(), any(Long.class))).thenReturn(true);
        when(runs.claimForExecution(eq("run"), eq(1), any(), any())).thenReturn(1);
        when(projects.selectById(1L)).thenReturn(project());

        consumer.consume(message(), channel, 14L);

        InOrder order = inOrder(runner, channel);
        order.verify(runner).run(eq("run"), eq("project"), any());
        order.verify(channel).basicAck(14L, false);
    }

    @Test
    void redisExceptionNacksForRedeliveryWithoutRunnerOrLockRelease() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("QUEUED"));
        when(redis.tryLock(any(), any(), any(Long.class))).thenThrow(new IllegalStateException("redis unavailable"));

        consumer.consume(message(), channel, 15L);

        verify(channel).basicNack(15L, false, true);
        verify(runner, never()).run(any(), any(), any());
        verify(redis, never()).releaseLock(any(), any());
    }

    @Test
    void retryableRunnerFailureIsPersistedThenHandedOffBeforeAck() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("QUEUED"));
        when(redis.tryLock(any(), any(), any(Long.class))).thenReturn(true);
        when(runs.claimForExecution(eq("run"), eq(1), any(), any())).thenReturn(1);
        when(projects.selectById(1L)).thenReturn(project());
        doThrow(new IllegalStateException("runner failed")).when(runner).run(any(), any(), any());
        when(classifier.classify(any())).thenReturn(WorkflowErrorCode.NETWORK_TIMEOUT);
        when(runs.recordRetryableFailure(eq("run"), eq("NETWORK_TIMEOUT"), any(), any(), any())).thenReturn(1);

        consumer.consume(message(), channel, 16L);

        verify(runs).recordRetryableFailure(eq("run"), eq("NETWORK_TIMEOUT"), any(), any(), any());
        verify(rabbitTemplate).convertAndSend(eq("workflow.retry"), eq("retry.30s"), any(WorkflowRunMessage.class), any(MessagePostProcessor.class));
        verify(channel).basicAck(16L, false);
        verify(redis).releaseLock(eq("workflow:execute:run"), any());
    }

    @Test
    void pendingDeliveryIsRequeuedUntilPublisherConfirmMakesTheRunQueued() throws Exception {
        when(runs.selectOne(any())).thenReturn(run("PENDING"));

        consumer.consume(message(), channel, 17L);

        verify(channel).basicNack(17L, false, true);
        verify(redis, never()).tryLock(any(), any(), any(Long.class));
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void retryDeliveryMustPassVersionedRetryWaitToQueuedTransitionBeforeClaim() throws Exception {
        WorkflowRun retryWait = run("RETRY_WAIT");
        when(runs.selectOne(any())).thenReturn(retryWait, run("SUCCESS"));
        when(runs.queueRetryForDelivery(eq("run"), eq(0L), any())).thenReturn(1);
        when(redis.tryLock(any(), any(), any(Long.class))).thenReturn(true);
        when(runs.claimForExecution(eq("run"), eq(1), eq(1L), any())).thenReturn(1);
        when(projects.selectById(1L)).thenReturn(project());

        consumer.consume(message(), channel, 18L);

        verify(runs).queueRetryForDelivery(eq("run"), eq(0L), any());
        verify(runner).run(eq("run"), eq("project"), any());
    }

    private WorkflowRunMessage message() {
        return new WorkflowRunMessage(1, "message", "event", "run", 1, "trace", LocalDateTime.now());
    }

    private WorkflowRun run(String status) {
        return WorkflowRun.builder().id(1L).workflowRunUuid("run").projectId(1L).attempt(1).statusVersion(0L).status(status).build();
    }

    private GameProject project() {
        GameProject project = new GameProject();
        project.setId(1L);
        project.setProjectUuid("project");
        return project;
    }
}
