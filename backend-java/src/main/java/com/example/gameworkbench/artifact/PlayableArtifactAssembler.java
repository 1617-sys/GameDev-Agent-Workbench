package com.example.gameworkbench.artifact;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GenerationRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class PlayableArtifactAssembler {
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final long MAX_PACKAGE_BYTES = 256L * 1024 * 1024;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|api[_-]?key\\s*[:=]|password\\s*[:=]|secret\\s*[:=]|bearer\\s+[a-z0-9._~-]{16,})");
    private final ObjectMapper json;

    public PlayableArtifactAssembler(ObjectMapper json) { this.json = json; }

    public PlayableArtifact assemble(GenerationRun run, Path cocosOutput, String buildLogDigest) {
        validate(run, cocosOutput, buildLogDigest);
        try {
            TreeMap<String, byte[]> files = new TreeMap<>();
            addGameFiles(cocosOutput, files);
            text(files, "provenance/game-spec.json", run.getCanonicalSpecJson());
            text(files, "provenance/runtime-ir.json", run.getRuntimeIrJson());
            text(files, "provenance/build-request.json", run.getBuildRequestJson());
            ObjectNode buildRecord = json.createObjectNode();
            buildRecord.put("generationRunUuid", run.getRunUuid());
            buildRecord.put("buildLogDigest", buildLogDigest);
            buildRecord.put("runtimeIrDigest", run.getRuntimeIrDigest());
            text(files, "evidence/build-record.json", write(sort(buildRecord)));
            text(files, "README.md", README);
            text(files, "launch.ps1", LAUNCHER);
            security(files);

            String payloadDigest = digestFiles(files);
            ObjectNode manifest = manifest(run, files, payloadDigest);
            text(files, "artifact-manifest.json", write(sort(manifest)));
            byte[] zip = zip(files);
            return new PlayableArtifact(run.getSourceDigest(), payloadDigest, digest(zip), manifest, zip);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to assemble playable artifact", exception);
        }
    }

    private void addGameFiles(Path root, Map<String, byte[]> files) throws Exception {
        long total = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList()) {
                if (Files.isSymbolicLink(file)) reject();
                Path relative = root.relativize(file).normalize();
                String name = relative.toString().replace('\\', '/');
                safePath(name);
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES || (total += size) > MAX_PACKAGE_BYTES) reject();
                files.put("game/" + name, Files.readAllBytes(file));
            }
        }
        if (!files.containsKey("game/index.html")) reject();
    }

    private ObjectNode manifest(GenerationRun run, TreeMap<String, byte[]> files, String payloadDigest) {
        ObjectNode root = json.createObjectNode();
        root.put("artifactContractVersion", "local-playable/1");
        root.put("artifactType", "LOCAL_COCOS_WEB_PACKAGE");
        root.put("generationRunUuid", run.getRunUuid());
        root.put("projectId", run.getProjectId());
        root.put("sourceDigest", run.getSourceDigest());
        root.put("payloadDigest", payloadDigest);
        root.put("packageDigestBinding", "persisted-after-zip-assembly");
        root.put("gameSpecDigest", run.getSourceDigest());
        root.put("runtimeIrDigest", run.getRuntimeIrDigest());
        JsonNode request = read(run.getBuildRequestJson());
        root.put("cocosCreatorVersion", request.path("cocosCreatorVersion").asText());
        root.put("runtimeShellVersion", request.path("runtimeShellVersion").asText());
        root.put("capabilityRegistryVersion", request.path("capabilityRegistryVersion").asText());
        root.put("buildProfileVersion", request.path("buildProfileVersion").asText());
        ArrayNode entries = root.putArray("files");
        files.forEach((path, bytes) -> entries.addObject().put("path", path).put("sha256", digest(bytes)).put("size", bytes.length));
        root.put("manifestSelfExcludedFromFileList", true);
        return root;
    }

    private void validate(GenerationRun run, Path output, String logDigest) {
        if (run == null || !"BUILDING".equals(run.getStatus())
                || run.getSourceDigest() == null || !run.getSourceDigest().matches("[0-9a-f]{64}")
                || run.getRuntimeIrDigest() == null || !run.getRuntimeIrDigest().matches("[0-9a-f]{64}")
                || logDigest == null || !logDigest.matches("[0-9a-f]{64}")
                || output == null || !Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) reject();
    }

    private void security(Map<String, byte[]> files) {
        files.forEach((path, bytes) -> {
            safePath(path);
            String lower = path.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".js") || lower.endsWith(".json")
                    || lower.endsWith(".md") || lower.endsWith(".ps1")) {
                String value = new String(bytes, StandardCharsets.UTF_8);
                if (SECRET.matcher(value).find()) reject();
            }
        });
    }

    private byte[] zip(TreeMap<String, byte[]> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.setLevel(9);
            for (var file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String digestFiles(TreeMap<String, byte[]> files) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        files.forEach((path, bytes) -> {
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(bytes);
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private void safePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || Stream.of(path.split("/")).anyMatch(".."::equals) || path.chars().anyMatch(value -> value < 32)) reject();
    }

    private void text(Map<String, byte[]> files, String path, String value) {
        if (value == null) reject();
        files.put(path, (value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing() + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private JsonNode sort(JsonNode input) {
        if (input.isObject()) {
            ObjectNode output = json.createObjectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            input.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> output.set(name, sort(input.get(name))));
            return output;
        }
        if (input.isArray()) {
            ArrayNode output = json.createArrayNode();
            input.forEach(value -> output.add(sort(value)));
            return output;
        }
        return input.deepCopy();
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void reject() { throw new BusinessException(ErrorCode.EXPORT_SECURITY_REJECTED); }

    private static final String README = """
            # Local Cocos Web Mobile Package

            Run `launch.ps1` from PowerShell. The launcher serves the bundled `game/` directory on localhost and opens it in the default browser.

            The game does not require the GameDev Agent Workbench, Java API, Python Agent, or a remote model at runtime.
            """;

    private static final String LAUNCHER = """
            $ErrorActionPreference = 'Stop'
            $gameRoot = Join-Path $PSScriptRoot 'game'
            if (-not (Test-Path -LiteralPath (Join-Path $gameRoot 'index.html'))) { throw 'game/index.html is missing' }
            $gameRootFull = [IO.Path]::GetFullPath($gameRoot).TrimEnd('\\') + '\\'
            $port = 4173
            $prefix = "http://127.0.0.1:$port/"
            $listener = [System.Net.HttpListener]::new()
            $listener.Prefixes.Add($prefix)
            $listener.Start()
            Start-Process $prefix
            Write-Host "Serving local game at $prefix. Press Ctrl+C to stop."
            try {
              while ($listener.IsListening) {
                $context = $listener.GetContext()
                $relative = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart('/'))
                if ([string]::IsNullOrWhiteSpace($relative)) { $relative = 'index.html' }
                $candidate = [IO.Path]::GetFullPath((Join-Path $gameRoot $relative))
                if (-not $candidate.StartsWith($gameRootFull, [StringComparison]::OrdinalIgnoreCase)) { $context.Response.StatusCode = 403; $context.Response.Close(); continue }
                if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { $context.Response.StatusCode = 404; $context.Response.Close(); continue }
                $bytes = [IO.File]::ReadAllBytes($candidate)
                $mime = switch ([IO.Path]::GetExtension($candidate).ToLowerInvariant()) { '.html' { 'text/html; charset=utf-8' } '.js' { 'text/javascript; charset=utf-8' } '.css' { 'text/css; charset=utf-8' } '.json' { 'application/json; charset=utf-8' } '.wasm' { 'application/wasm' } '.png' { 'image/png' } '.jpg' { 'image/jpeg' } '.svg' { 'image/svg+xml' } default { 'application/octet-stream' } }
                $context.Response.ContentType = $mime
                $context.Response.ContentLength64 = $bytes.Length
                $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                $context.Response.Close()
              }
            } finally { $listener.Stop(); $listener.Close() }
            """;
}
