package com.example.gameworkbench.cocos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 使用固定 Cocos Runtime Shell 生成 Web Mobile 包的本地构建执行器。
 *
 * <p>执行器复制受信任的 Runtime Shell 到独立临时工作区，只写入 Java 编译产生的
 * Runtime IR，并使用固定 CLI 参数启动 Cocos。路径规范化、符号链接拒绝和构建超时
 * 用于缩小文件系统与进程风险。</p>
 *
 * <p>“独立工作区”不是操作系统或容器级安全沙箱；当前实现适合受控的单机开发环境。</p>
 */
@Component
public class CocosBuildWorker {
    private static final String TARGET = "web-mobile";
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final Set<String> GENERATED_PROJECT_DIRECTORIES = Set.of(
            ".git", "build", "library", "local", "native", "node_modules", "profiles", "temp");
    private final ObjectMapper json;
    private final Path executable;
    private final Path runtimeProject;
    private final Path workRoot;
    private final Set<Integer> successExitCodes;

    public CocosBuildWorker(ObjectMapper json,
            @Value("${app.cocos.executable:}") String executable,
            @Value("${app.cocos.runtime-project:}") String runtimeProject,
            @Value("${app.cocos.work-root:${java.io.tmpdir}/gamedev-cocos-builds}") String workRoot,
            @Value("${app.cocos.success-exit-codes:36}") String successExitCodes) {
        this.json = json;
        this.executable = path(executable);
        this.runtimeProject = path(runtimeProject);
        this.workRoot = Path.of(workRoot).toAbsolutePath().normalize();
        this.successExitCodes = Stream.of(successExitCodes.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(Integer::parseInt).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean available() {
        return executable != null && runtimeProject != null
                && Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
                && Files.isDirectory(runtimeProject, LinkOption.NOFOLLOW_LINKS);
    }

    public CocosBuildResult build(ObjectNode buildRequest, ObjectNode runtimeIr) {
        validate(buildRequest, runtimeIr);
        if (!available()) throw new BusinessException(ErrorCode.COCOS_BUILD_UNAVAILABLE);
        Path workspace = workRoot.resolve("build-" + UUID.randomUUID()).normalize();
        requireChild(workRoot, workspace);
        Path project = workspace.resolve("project");
        Path output = workspace.resolve("output");
        Path log = workspace.resolve("cocos-build.log");
        try {
            Files.createDirectories(workspace);
            // SECURITY: 只复制固定 Runtime Shell，并排除缓存、构建结果、依赖和版本库目录。
            // 用户不能通过 BuildRequest 指定源目录、可执行文件或额外命令行参数。
            copyTree(runtimeProject, project);
            Path generated = project.resolve("assets/resources/generated/runtime-ir.json").normalize();
            requireChild(project, generated);
            Files.createDirectories(generated.getParent());
            Files.writeString(generated, json.writeValueAsString(runtimeIr), StandardCharsets.UTF_8);
            Files.createDirectories(output);
            String buildOptions = "platform=" + TARGET + ";buildPath=" + output + ";debug=false";
            Process process = new ProcessBuilder(executable.toString(), "--project", project.toString(), "--build", buildOptions)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                // FAILURE: 超时后强制终止子进程，并保留构建日志摘要用于诊断；不信任残留输出。
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                return new CocosBuildResult(CocosBuildResult.Status.FAILED, -1, digestFile(log), null, null);
            }
            int exit = process.exitValue();
            if (!successExitCodes.contains(exit) || emptyDirectory(output)) {
                return new CocosBuildResult(CocosBuildResult.Status.FAILED, exit, digestFile(log), null, null);
            }
            Path playableRoot = playableRoot(output);
            if (playableRoot == null) {
                return new CocosBuildResult(CocosBuildResult.Status.FAILED, exit, digestFile(log), null, null);
            }
            return new CocosBuildResult(CocosBuildResult.Status.SUCCEEDED, exit, digestFile(log), digestTree(playableRoot), playableRoot);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cocos build interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cocos build failed before producing a result", exception);
        }
    }

    private void validate(ObjectNode request, ObjectNode ir) {
        if (request == null || ir == null || !TARGET.equals(request.path("target").asText())
                || !request.path("gameSpecDigest").asText().matches("[0-9a-f]{64}")
                || !request.path("runtimeIrDigest").asText().matches("[0-9a-f]{64}")
                || !request.path("gameSpecDigest").asText().equals(ir.path("sourceDigest").asText())
                || !request.path("runtimeIrDigest").asText().equals(ir.path("runtimeIrDigest").asText())) {
            throw new BusinessException(ErrorCode.GAMESPEC_INVALID);
        }
    }

    void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Path relative = source.relativize(directory);
                if (relative.getNameCount() == 1 && GENERATED_PROJECT_DIRECTORIES.contains(relative.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                copyDirectory(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Runtime project cannot contain symbolic links");
                Path destination = destination(file);
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }

            private void copyDirectory(Path directory) throws IOException {
                Files.createDirectories(destination(directory));
            }

            private Path destination(Path value) {
                Path destination = target.resolve(source.relativize(value)).normalize();
                requireChild(target, destination);
                return destination;
            }
        });
    }

    private boolean emptyDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) { return files.noneMatch(Files::isRegularFile); }
    }

    private Path playableRoot(Path output) throws IOException {
        try (Stream<Path> files = Files.walk(output)) {
            List<Path> indexes = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("index.html"))
                    .sorted().toList();
            return indexes.size() == 1 ? indexes.get(0).getParent() : null;
        }
    }

    private String digestTree(Path directory) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList()) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String digestFile(Path file) throws Exception {
        if (!Files.exists(file)) return null;
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private void requireChild(Path root, Path value) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!value.toAbsolutePath().normalize().startsWith(normalizedRoot)) throw new IllegalArgumentException("Path escapes build root");
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }
}
