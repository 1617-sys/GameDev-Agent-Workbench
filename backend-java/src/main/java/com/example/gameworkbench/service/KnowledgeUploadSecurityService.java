package com.example.gameworkbench.service;

import java.security.MessageDigest;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;

/** Validates untrusted upload bytes before any storage write. */
@Service
public class KnowledgeUploadSecurityService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    public ValidatedUpload validate(String fileName, String mimeType, byte[] bytes) {
        if (fileName == null || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) fail();
        String normalized = fileName.replace('\\', '/');
        if (normalized.contains("../") || normalized.contains("\u0000")) fail();
        String extension = normalized.contains(".") ? normalized.substring(normalized.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        boolean pdf = extension.equals("pdf") && "application/pdf".equalsIgnoreCase(mimeType)
                && bytes.length >= 5 && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        boolean text = (extension.equals("md") || extension.equals("markdown") || extension.equals("txt"))
                && ("text/plain".equalsIgnoreCase(mimeType) || "text/markdown".equalsIgnoreCase(mimeType))
                && noNul(bytes);
        if (!pdf && !text) fail();
        return new ValidatedUpload(extension, sha256(bytes));
    }

    private static boolean noNul(byte[] bytes) { for (byte value : bytes) if (value == 0) return false; return true; }
    private static String sha256(byte[] bytes) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder out = new StringBuilder(64); for (byte b : hash) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void fail() { throw new BusinessException(ErrorCode.INVALID_PARAM); }
    public record ValidatedUpload(String extension, String contentHash) { }
}
