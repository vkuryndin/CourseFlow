package org.courseflow.integration.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Small JSON assertion helpers shared by the ported HTTP scenarios. */
public final class JsonChecks {
  public static final Object UNDEFINED = new Object();
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private JsonChecks() {}

  public static Object parse(String json) {
    try {
      return JSON.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON response", e);
    }
  }

  public static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize test data", e);
    }
  }

  public static Object field(Object value, Object key) {
    if (value instanceof Map<?, ?> map) {
      return map.containsKey(key) ? map.get(key) : UNDEFINED;
    }
    if (value instanceof List<?> list) {
      if ("length".equals(key)) return list.size();
      if (key instanceof Number n) {
        int index = n.intValue();
        return index >= 0 && index < list.size() ? list.get(index) : UNDEFINED;
      }
    }
    if (value instanceof String text && "length".equals(key)) return text.length();
    return UNDEFINED;
  }

  /** Strict JSON contract checks, without JavaScript coercion. */
  public static Object requiredField(Object value, String key) {
    if (!(value instanceof Map<?, ?> map) || !map.containsKey(key)) {
      throw new AssertionError("Missing JSON field: " + key);
    }
    return map.get(key);
  }

  public static long requiredInteger(Object value, String key) {
    Object field = requiredField(value, key);
    if (!(field instanceof Byte || field instanceof Short || field instanceof Integer || field instanceof Long)) {
      throw new AssertionError("Expected integer JSON field: " + key);
    }
    return ((Number) field).longValue();
  }

  public static boolean has(Object value, Object key) {
    return value instanceof Map<?, ?> map && map.containsKey(key);
  }

  public static boolean isArray(Object value) {
    return value instanceof List<?>;
  }

  public static List<Object> array(Object... values) {
    return Arrays.asList(values);
  }

  public static boolean eq(Object left, Object right) {
    if (left instanceof Number a && right instanceof Number b) {
      return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    }
    return Objects.equals(left, right);
  }

  public static boolean looseEq(Object left, Object right) {
    return (left == null || left == UNDEFINED) && (right == null || right == UNDEFINED)
        || eq(left, right);
  }

  public static String typeOf(Object value) {
    if (value == UNDEFINED) return "undefined";
    if (value instanceof String) return "string";
    if (value instanceof Boolean) return "boolean";
    if (value instanceof Number) return "number";
    return "object";
  }

  public static boolean truth(Object value) {
    if (value == UNDEFINED || value == null) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.doubleValue() != 0 && !Double.isNaN(n.doubleValue());
    if (value instanceof String s) return !s.isEmpty();
    return true;
  }

  public static String str(Object value) {
    if (value == UNDEFINED) return "undefined";
    if (value instanceof Number n && n.doubleValue() == n.longValue()) {
      return Long.toString(n.longValue());
    }
    return String.valueOf(value);
  }

  public static double number(Object value) {
    if (value instanceof Number n) return n.doubleValue();
    if (value == null) return 0;
    if (value instanceof Boolean b) return b ? 1 : 0;
    return Double.parseDouble(str(value));
  }

  public static Object plus(Object left, Object right) {
    if (left instanceof String || right instanceof String) return str(left) + str(right);
    return number(left) + number(right);
  }

  public static double subtract(Object left, Object right) {
    return number(left) - number(right);
  }

  public static boolean gt(Object left, Object right) {
    return number(left) > number(right);
  }

  public static boolean ge(Object left, Object right) {
    return number(left) >= number(right);
  }

  public static boolean lt(Object left, Object right) {
    return number(left) < number(right);
  }

  public static boolean le(Object left, Object right) {
    return number(left) <= number(right);
  }

  public static long instant(Object value) {
    return Instant.parse(str(value)).toEpochMilli();
  }

  public static boolean matches(Pattern pattern, Object value) {
    return pattern.matcher(str(value)).find();
  }

  private static List<?> list(Object value) {
    if (value instanceof List<?> result) return result;
    throw new AssertionError("Expected JSON array, got " + typeOf(value));
  }

  public static boolean some(Object values, Predicate<Object> predicate) {
    return list(values).stream().anyMatch(predicate);
  }

  public static boolean every(Object values, Predicate<Object> predicate) {
    return list(values).stream().allMatch(predicate);
  }

  public static Object find(Object values, Predicate<Object> predicate) {
    for (Object value : list(values)) if (predicate.test(value)) return value;
    return UNDEFINED;
  }

  public static List<?> filter(Object values, Predicate<Object> predicate) {
    return list(values).stream().filter(predicate).toList();
  }

  public static int indexOf(Object values, Object target) {
    List<?> list = list(values);
    for (int i = 0; i < list.size(); i++) if (eq(list.get(i), target)) return i;
    return -1;
  }

  public static boolean includes(Object values, Object target) {
    return values instanceof String text
        ? text.contains(str(target))
        : indexOf(values, target) >= 0;
  }

  public static List<String> split(Object value, String delimiter) {
    return Arrays.asList(str(value).split(Pattern.quote(delimiter), -1));
  }

  public static void each(Object values, Consumer<Object> action) {
    list(values).forEach(action);
  }

  public static void each(Object values, BiConsumer<Object, Integer> action) {
    List<?> list = list(values);
    for (int i = 0; i < list.size(); i++) action.accept(list.get(i), i);
  }

  public static void verify(boolean condition, Object message) {
    assertTrue(condition, () -> str(message));
  }
}
