package dev.stardew.assistant.crop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CropRulesTest {
  @Test
  void unknownExplicitRuleIsNotBaseline() {
    var rules = new HashMap<String, Object>();
    rules.put("fertilizer", null);
    assertFalse(CropRules.supports(rules));
  }
}
