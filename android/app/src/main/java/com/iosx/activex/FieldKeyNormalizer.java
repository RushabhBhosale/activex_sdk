package com.iosx.activex;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FieldKeyNormalizer {
  private FieldKeyNormalizer() {}

  static String toPublicKey(String sdkFieldName) {
    if (sdkFieldName == null || sdkFieldName.isEmpty()) {
      return sdkFieldName;
    }

    String fieldName = sdkFieldName;
    if (fieldName.length() > 2
      && fieldName.startsWith("pp")
      && Character.isUpperCase(fieldName.charAt(2))) {
      fieldName = fieldName.substring(2);
    }

    return lowerInitialWord(fieldName);
  }

  static Object toPublicValue(Object sdkValue) {
    if (sdkValue == null) {
      return "N/A";
    }
    if (sdkValue instanceof Number || sdkValue instanceof Boolean) {
      return sdkValue;
    }
    if (sdkValue instanceof CharSequence || sdkValue instanceof Character || sdkValue instanceof Enum) {
      return toPublicString(String.valueOf(sdkValue));
    }
    if (sdkValue instanceof Map<?, ?>) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) sdkValue).entrySet()) {
        result.put(toPublicKey(String.valueOf(entry.getKey())), toPublicValue(entry.getValue()));
      }
      return result;
    }
    if (sdkValue instanceof Iterable<?>) {
      List<Object> result = new ArrayList<>();
      for (Object item : (Iterable<?>) sdkValue) {
        result.add(toPublicValue(item));
      }
      return result;
    }
    if (sdkValue.getClass().isArray()) {
      List<Object> result = new ArrayList<>();
      int length = Array.getLength(sdkValue);
      for (int i = 0; i < length; i++) {
        result.add(toPublicValue(Array.get(sdkValue, i)));
      }
      return result;
    }

    return toPublicString(String.valueOf(sdkValue));
  }

  static String toPublicString(String sdkValue) {
    if (sdkValue == null || sdkValue.isEmpty()) {
      return sdkValue;
    }

    return sdkValue
      .replace("com.lefu.", "")
      .replaceAll("\\bPP_", "")
      .replaceAll("\\bPP(?=[A-Z])", "");
  }

  private static String lowerInitialWord(String value) {
    if (value.isEmpty() || Character.isLowerCase(value.charAt(0))) {
      return value;
    }

    int end = 1;
    while (end < value.length() && Character.isUpperCase(value.charAt(end))) {
      end++;
    }
    if (end > 1 && end < value.length() && Character.isLowerCase(value.charAt(end))) {
      end--;
    }

    return value.substring(0, end).toLowerCase() + value.substring(end);
  }
}
