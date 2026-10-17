package dev.stardew.assistant.common;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;

public final class ExactInputs {
  private ExactInputs() {}

  public static ObjectMapper mapper(ObjectMapper original) {
    var result = original.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    result
        .coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
        .setCoercion(
            com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
            com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    return result;
  }

  public static Long number(Object value) {
    if (value == null) return null;
    if (!(value instanceof Number)) throw new IllegalArgumentException("数量必须为整数");
    try {
      return new BigDecimal(value.toString()).longValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalArgumentException("整数超出范围或含小数", e);
    }
  }

  public static Map<String, Long> numbers(Object value) {
    if (!(value instanceof Map<?, ?> values)) throw new IllegalArgumentException("库存必须为对象");
    var result = new LinkedHashMap<String, Long>();
    values.forEach(
        (k, v) -> {
          if (!(k instanceof String s) || v == null) throw new IllegalArgumentException("库存非法");
          result.put(s, number(v));
        });
    return result;
  }
}
