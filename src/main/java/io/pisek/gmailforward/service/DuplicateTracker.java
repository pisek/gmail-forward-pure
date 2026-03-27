package io.pisek.gmailforward.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class DuplicateTracker {

    private static final Logger log = Logger.getLogger(DuplicateTracker.class.getName());

    private final Path basePath;

    public DuplicateTracker(String dbPath) throws IOException {
        this.basePath = Path.of(dbPath);
        Files.createDirectories(basePath);
    }

    public List<MessageData> filterUnseen(String accountName, List<MessageData> messages) throws IOException {
        Set<String> seen = loadSeen(accountName);
        return messages.stream()
                .filter(m -> !seen.contains(m.messageId()))
                .toList();
    }

    public void markSeen(String accountName, String messageId) throws IOException {
        Path file = seenFile(accountName);
        Files.writeString(file, messageId + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Set<String> loadSeen(String accountName) throws IOException {
        Path file = seenFile(accountName);
        if (!Files.exists(file)) {
            return Set.of();
        }
        List<String> lines = Files.readAllLines(file);
        Set<String> seen = new HashSet<>(lines.size());
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
        return seen;
    }

    private Path seenFile(String accountName) {
        // Sanitize account name for use as filename
        String safeName = accountName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return basePath.resolve(safeName + ".seen");
    }
}
