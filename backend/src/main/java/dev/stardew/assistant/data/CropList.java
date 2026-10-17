package dev.stardew.assistant.data;

import java.util.List;

public record CropList(List<GameEntity> crops, int count, boolean completeWithinDataset) {}
