package com.example.gameworkbench.security;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserCapabilityService {

    public static final String ADMIN_DASHBOARD = "admin.dashboard";
    public static final String ADMIN_USERS_MANAGE = "admin.users.manage";

    private static final List<String> USER = List.of(
            "projects.read",
            "projects.create",
            "generation.read",
            "generation.compile",
            "generation.build",
            "artifacts.read",
            "prototype-versions.read"
    );

    private static final List<String> PROJECT_ADVANCED = List.of(
            "projects.update",
            "generation.author",
            "generation.approve",
            "generation.release",
            "prototype-versions.manage",
            "player-runs.create",
            "player-runs.read",
            "machine-episodes.read",
            "knowledge.read",
            "knowledge.upload",
            "workflow-runs.manage",
            "director-runs.manage",
            "playtest.manage",
            "exports.manage"
    );

    private static final List<String> ADMIN = List.of(
            ADMIN_DASHBOARD,
            "admin.agent-runs",
            "admin.diagnostics",
            ADMIN_USERS_MANAGE,
            "prompt-ops.manage",
            "prompt-analytics.read"
    );

    public List<String> forRole(String role) {
        String normalized = role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT);
        Set<String> capabilities = new LinkedHashSet<>(USER);
        if ("PROJECT_ADVANCED".equals(normalized) || "ADMIN".equals(normalized)) {
            capabilities.addAll(PROJECT_ADVANCED);
        }
        if ("ADMIN".equals(normalized)) {
            capabilities.addAll(ADMIN);
        }
        return List.copyOf(new ArrayList<>(capabilities));
    }
}
