package com.iosx.activex;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MeasurementValidationClient {
  private static final String TAG = "MeasurementValidation";
  private static final String API_URL = "http://dev-api.myactivex.com/external/SdkDataPost";
  private static final String API_KEY_HEADER = "x-api-key";
  private static final int CONNECT_TIMEOUT_MS = 15000;
  private static final int READ_TIMEOUT_MS = 15000;
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
  private static final boolean LOG_REQUEST_DETAILS = true;
  private static final int LOG_CHUNK_SIZE = 3000;

  interface Callback {
    void onAuthorized();
    void onRejected(String message, Throwable error);
  }

  void validate(MeasurementInput input, ResultData measurement, Callback callback) {
    String validationError = input == null ? "Measurement input is required." : input.validationError();
    if (validationError != null) {
      callback.onRejected(validationError, null);
      return;
    }

    EXECUTOR.execute(() -> {
      HttpURLConnection connection = null;
      try {
        URL url = new URL(API_URL);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty(API_KEY_HEADER, input.getActiveXSecret());

        String requestBodyString = buildPayload(input, measurement).toString();
        if (LOG_REQUEST_DETAILS) {
          Log.d(TAG, "Request URL: " + API_URL);
          Log.d(TAG, "Request method: POST");
          Log.d(TAG, "Request headers: {Content-Type=application/json; charset=UTF-8, "
            + "Accept=application/json, "
            + API_KEY_HEADER + "=" + input.getActiveXSecret() + "}");
          logInChunks("Request payload", requestBodyString);
        }

        byte[] requestBody = requestBodyString.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(requestBody.length);
        try (OutputStream output = connection.getOutputStream()) {
          output.write(requestBody);
        }

        int responseCode = connection.getResponseCode();
        String responseBody = readResponse(connection, responseCode);
        if (LOG_REQUEST_DETAILS) {
          Log.d(TAG, "Response: HTTP " + responseCode);
          logInChunks("Response body", responseBody);
        }
        if (responseCode >= 200 && responseCode < 300 && isAllowed(responseBody)) {
          callback.onAuthorized();
        } else {
          callback.onRejected(
            "Measurement authorization failed (HTTP " + responseCode + ").",
            null
          );
        }
      } catch (Exception error) {
        Log.e(TAG, "Measurement authorization request failed", error);
        callback.onRejected("Could not authorize measurement data.", error);
      } finally {
        if (connection != null) connection.disconnect();
      }
    });
  }

  private static void logInChunks(String label, String value) {
    if (value == null || value.isEmpty()) {
      Log.d(TAG, label + " [1/1]: <empty>");
      return;
    }

    int totalChunks = (value.length() + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE;
    for (int start = 0, chunk = 1; start < value.length(); start += LOG_CHUNK_SIZE, chunk++) {
      int end = Math.min(start + LOG_CHUNK_SIZE, value.length());
      Log.d(TAG, label + " [" + chunk + "/" + totalChunks + "]: " + value.substring(start, end));
    }
  }

  private static JSONObject buildPayload(MeasurementInput input, ResultData measurement)
    throws JSONException {
    JSONObject payload = new JSONObject();

    if (measurement != null) {
      for (Map.Entry<String, Object> entry : measurement.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        // The API expects every calculated device field directly at the root.
        if ("fields".equals(key) && value instanceof Map<?, ?>) {
          for (Map.Entry<?, ?> field : ((Map<?, ?>) value).entrySet()) {
            payload.put(String.valueOf(field.getKey()), toJsonValue(field.getValue()));
          }
        } else {
          payload.put(key, toJsonValue(value));
        }
      }
    }

    // Explicit input values win over any device field with the same name.
    payload.put("UniquePatientId", input.getUniquePatientId());
    payload.put("Date", input.getDate());
    payload.put("PatientName", input.getPatientName());
    return payload;
  }

  private static Object toJsonValue(Object value) throws JSONException {
    if (value == null) return JSONObject.NULL;
    if (value instanceof JSONObject || value instanceof JSONArray
      || value instanceof Number || value instanceof Boolean || value instanceof String) {
      return value;
    }
    if (value instanceof Character || value instanceof Enum<?>) {
      return String.valueOf(value);
    }
    if (value instanceof Map<?, ?>) {
      JSONObject object = new JSONObject();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        object.put(String.valueOf(entry.getKey()), toJsonValue(entry.getValue()));
      }
      return object;
    }
    if (value instanceof Iterable<?>) {
      JSONArray array = new JSONArray();
      for (Object item : (Iterable<?>) value) {
        array.put(toJsonValue(item));
      }
      return array;
    }
    if (value.getClass().isArray()) {
      JSONArray array = new JSONArray();
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        array.put(toJsonValue(Array.get(value, i)));
      }
      return array;
    }
    return String.valueOf(value);
  }

  private static String readResponse(HttpURLConnection connection, int responseCode)
    throws IOException {
    InputStream stream = responseCode >= 400
      ? connection.getErrorStream()
      : connection.getInputStream();
    if (stream == null) return "";

    StringBuilder body = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        body.append(line);
      }
    }
    return body.toString();
  }

  private static boolean isAllowed(String responseBody) {
    if (responseBody == null || responseBody.trim().isEmpty()) return true;

    try {
      Object response = new JSONObject(responseBody);
      Boolean explicitDecision = findDecision(response);
      return explicitDecision == null || explicitDecision;
    } catch (JSONException ignored) {
      // A successful non-JSON response is still treated as an authorized request.
      return true;
    }
  }

  private static Boolean findDecision(Object value) {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      String[] decisionKeys = {"isAllowed", "allowed", "isAuthorized", "authorized", "success"};
      Iterator<String> decisionKeyIterator = object.keys();
      while (decisionKeyIterator.hasNext()) {
        String responseKey = decisionKeyIterator.next();
        for (String key : decisionKeys) {
          if (!key.equalsIgnoreCase(responseKey) || object.isNull(responseKey)) continue;
          Object decision = object.opt(responseKey);
          if (decision instanceof Boolean) return (Boolean) decision;
          if (decision instanceof String) return Boolean.valueOf((String) decision);
        }
      }

      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Boolean nestedDecision = findDecision(object.opt(key));
        if (nestedDecision != null) return nestedDecision;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        Boolean nestedDecision = findDecision(array.opt(i));
        if (nestedDecision != null) return nestedDecision;
      }
    }
    return null;
  }
}
