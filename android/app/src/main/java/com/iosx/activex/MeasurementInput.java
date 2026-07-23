package com.iosx.activex;

/**
 * Caller-provided context used to authorize a completed measurement.
 *
 * The activeXSecret is sent as the x-api-key request header and is never
 * included in the measurement JSON body or returned to the caller.
 */
public final class MeasurementInput {
  private final String uniquePatientId;
  private final String date;
  private final String patientName;
  private final String activeXSecret;

  public MeasurementInput(
    String uniquePatientId,
    String date,
    String patientName,
    String activeXSecret
  ) {
    this.uniquePatientId = uniquePatientId;
    this.date = date;
    this.patientName = patientName;
    this.activeXSecret = activeXSecret;
  }

  public String getUniquePatientId() {
    return uniquePatientId;
  }

  public String getDate() {
    return date;
  }

  public String getPatientName() {
    return patientName;
  }

  public String getActiveXSecret() {
    return activeXSecret;
  }

  String validationError() {
    if (isBlank(uniquePatientId)) return "UniquePatientId is required.";
    if (isBlank(date)) return "Date is required.";
    if (isBlank(patientName)) return "PatientName is required.";
    if (isBlank(activeXSecret)) return "activeXSecret is required.";
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
