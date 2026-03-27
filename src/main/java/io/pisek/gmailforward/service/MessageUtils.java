package io.pisek.gmailforward.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class MessageUtils {

    private MessageUtils() {}

    public static String extractMessageId(byte[] rawContent) {
        String headers = extractHeaders(rawContent);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("message-id:")) {
                String value = line.substring("message-id:".length()).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return computeSha256(rawContent);
    }

    private static String extractHeaders(byte[] rawContent) {
        String content = new String(rawContent, StandardCharsets.UTF_8);
        int headerEnd = content.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            headerEnd = content.indexOf("\n\n");
        }
        if (headerEnd < 0) {
            return content;
        }
        // Unfold headers (continuation lines start with whitespace)
        String rawHeaders = content.substring(0, headerEnd);
        return rawHeaders.replaceAll("\r\n[ \t]+", " ").replaceAll("\n[ \t]+", " ");
    }

    private static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
