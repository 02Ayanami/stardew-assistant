package dev.stardew.assistant.crop;

import dev.stardew.assistant.common.GameDate;

public record HarvestEvent(GameDate date, long quantity, long revenue) {}
