package com.example.gameworkbench.gamespec;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

/**
 * 将模型生成与 Java 编译诊断组合成有界修复循环。
 *
 * <p>每轮由模型产生一个候选 GameSpec，再由 {@link GameSpecCompiler} 做权威校验；
 * 诊断会作为下一轮输入，但模型最多尝试三次，防止无限循环和无界成本。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecAuthorService {
    private static final int MAX_ATTEMPTS = 3;
    private final GameSpecApplicationService gameSpecs;
    private final SpecAuthorModel model;
    private final ObjectMapper json;

    public SpecAuthorResult author(Long userId, String projectUuid, String idea, ObjectNode initialSpec) {
        // SECURITY: 在任何付费模型调用之前先校验项目所有权。
        // 空对象预编译产生的诊断也能为第一轮模型提供明确的修复目标。
        GameSpecCompilationResult compilation = gameSpecs.compile(userId, projectUuid,
                initialSpec == null ? json.createObjectNode() : initialSpec);
        ObjectNode candidate = initialSpec;
        List<SpecAuthorAttempt> attempts = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SpecAuthorModelResponse authored = model.author(new SpecAuthorModelRequest(
                    userId, projectUuid, idea, candidate, diagnostics(compilation), attempt));
            candidate = authored.spec();
            compilation = gameSpecs.compile(userId, projectUuid, candidate);
            boolean accepted = compilation.status() == GameSpecCompilationResult.Status.SUCCEEDED;
            attempts.add(new SpecAuthorAttempt(attempt, candidate.deepCopy(), compilation.diagnostics(), accepted,
                    authored.modelEvidence() == null ? json.createObjectNode() : authored.modelEvidence().deepCopy()));
            if (accepted) return new SpecAuthorResult("SUCCEEDED", compilation.canonicalSpec(), compilation, List.copyOf(attempts));
        }
        return new SpecAuthorResult("FAILED", candidate, compilation, List.copyOf(attempts));
    }

    private String diagnostics(GameSpecCompilationResult compilation) {
        if (compilation == null || compilation.diagnostics().isEmpty()) return "none";
        try { return json.writeValueAsString(compilation.diagnostics()); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
