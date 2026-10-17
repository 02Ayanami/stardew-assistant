package dev.stardew.assistant.conversation;

import java.time.Instant;

public record ChatSession(String id, String title, Instant createdAt, Instant updatedAt) {}
