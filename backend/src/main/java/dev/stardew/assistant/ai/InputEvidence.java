package dev.stardew.assistant.ai;

import java.util.*;

/** Ephemeral evidence reconstructed from human messages, never persisted as task state. */
record InputEvidence(
    String latest,
    String previous,
    List<String> userMessages,
    Map<String, Object> confirmed,
    boolean clarification) {}
