package io.pisek.gmailforward.service;

public record MessageData(String messageId, byte[] rawContent, int messageNumber) {
}
