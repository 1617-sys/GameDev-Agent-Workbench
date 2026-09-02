package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.gamespec.CompileGameSpecRequest;
import com.example.gameworkbench.dto.gamespec.AuthorGameSpecRequest;
import com.example.gameworkbench.gamespec.GameSpecApplicationService;
import com.example.gameworkbench.gamespec.GameSpecCompilationResult;
import com.example.gameworkbench.gamespec.SpecAuthorResult;
import com.example.gameworkbench.gamespec.SpecAuthorService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * GameSpec 能力查询、编译和 AI 创作的协议层入口。
 *
 * <p>编译接口适合用户手工编辑后的即时校验；author 接口则把自然语言交给有界修复循环。
 * 两条路径最终都以同一个 Java 编译器为准，不能由前端或模型绕过业务规则。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v5")
public class GameSpecController {
    private final GameSpecApplicationService service;
    private final SpecAuthorService specAuthor;

    @GetMapping("/gamespec/capabilities")
    public ApiResponse<ObjectNode> capabilities() {
        // 前端用这份快照展示当前真正支持的玩法、资源配置和构建目标。
        return ApiResponse.success(service.capabilities());
    }

    @PostMapping("/projects/{projectUuid}/gamespec/compile")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.compile')")
    public ApiResponse<GameSpecCompilationResult> compile(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @Valid @RequestBody CompileGameSpecRequest request) {
        return ApiResponse.success(service.compile(userId, projectUuid, request.spec()));
    }

    @PostMapping("/projects/{projectUuid}/gamespec/author")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.author')")
    public ApiResponse<SpecAuthorResult> author(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @Valid @RequestBody AuthorGameSpecRequest request) {
        // currentSpec 可为空：为空表示从创意生成，非空表示基于现有规格修改或修复。
        return ApiResponse.success(specAuthor.author(userId, projectUuid, request.idea(), request.currentSpec()));
    }
}
