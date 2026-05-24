package com.example.gameworkbench.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    INVALID_PARAM(40001, "请求参数不合法", "统一参数校验失败"),
    AGENT_TYPE_REQUIRED(40001, "Agent类型不能为空", "Agent调用参数校验"),
    USERNAME_ALREADY_EXISTS(40002, "用户名已存在", "注册"),
    INVALID_USERNAME_OR_PASSWORD(40003, "用户名或密码错误", "登录"),
    ACCOUNT_DISABLED(40004, "账号已被禁用", "登录/获取当前用户"),

    UNAUTHORIZED(40101, "请先登录", "未登录或未携带有效身份"),
    TOKEN_INVALID_OR_EXPIRED(40101, "Token无效或已过期", "JWT鉴权"),

    FORBIDDEN_PROJECT_ACCESS(40301, "无权访问该项目", "项目详情"),
    FORBIDDEN_PROJECT_UPDATE(40301, "无权更新该项目", "项目更新"),
    FORBIDDEN_ARTIFACT_ACCESS(40301, "无权访问该产物", "产物详情"),

    USER_NOT_FOUND(40401, "用户不存在", "获取当前用户"),
    PROJECT_NOT_FOUND(40401, "项目不存在", "项目/Agent运行/工作流"),
    AGENT_RUN_NOT_FOUND(40401, "执行记录不存在", "执行记录详情"),
    ARTIFACT_NOT_FOUND(40401, "产物不存在", "产物详情"),
    WORKFLOW_RUN_NOT_FOUND(40401, "工作流记录不存在", "工作流详情"),

    SYSTEM_ERROR(50000, "系统异常", "全局兜底异常"),
    AGENT_RUN_ERROR(50001, "Agent执行失败", "Agent运行异常"),
    PYTHON_BASE_URL_NOT_CONFIGURED(50002, "Python服务地址未配置", "Python客户端配置"),

    PYTHON_CALL_FAILED(50201, "调用Python服务失败", "HTTP调用失败"),
    PYTHON_EMPTY_RESPONSE(50202, "Python服务未返回结果", "空响应"),
    PYTHON_INVALID_RESPONSE(50202, "Python服务返回格式不正确", "返回结构异常"),
    PYTHON_RESPONSE_PARSE_FAILED(50202, "解析Python返回结果失败", "JSON解析异常"),
    PYTHON_RESPONSE_FAILED(50203, "Python服务返回失败", "Python业务失败");

    private final int code;
    private final String message;
    private final String scene;

    ErrorCode(int code, String message, String scene) {
        this.code = code;
        this.message = message;
        this.scene = scene;
    }
}
