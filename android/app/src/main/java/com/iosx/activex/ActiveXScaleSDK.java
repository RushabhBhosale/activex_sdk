package com.iosx.activex;

import android.app.Activity;
import android.content.Context;

public class ActiveXScaleSDK {
  private static final String UNSUPPORTED_FLOW_MESSAGE =
    "This SDK build only supports " + LefuPlugin.SUPPORTED_DEVICE_NAME
      + " through the Lefu Borre flow.";

  private final LefuPlugin lefuScale;

  public ActiveXScaleSDK(Context context) {
    lefuScale = new LefuPlugin(context);
  }

  public void setEventListener(LefuEventListener eventListener) {
    lefuScale.setEventListener(eventListener);
  }

  public LefuPlugin getLefuScale() {
    return lefuScale;
  }

  public JambulLefuPlugin getJambulScale() {
    throw new UnsupportedOperationException(UNSUPPORTED_FLOW_MESSAGE);
  }

  public IceLefuPlugin getIceLefuScale() {
    throw new UnsupportedOperationException(UNSUPPORTED_FLOW_MESSAGE);
  }

  private void rejectUnsupportedFlow(ResultCallback callback) {
    if (callback != null) callback.onError(UNSUPPORTED_FLOW_MESSAGE, null);
  }

  public void initializeLefu(ResultCallback callback) {
    lefuScale.initializeSDK(callback);
  }

  public void startLefuScan(ResultCallback callback) {
    lefuScale.startScan(callback);
  }

  public void stopLefuScan(ResultCallback callback) {
    lefuScale.stopScan(callback);
  }

  public void connectLefuDevice(String deviceAddress, String deviceName, ResultCallback callback) {
    lefuScale.connectToDevice(deviceAddress, deviceName, callback);
  }

  public void checkLefuConnection(ResultCallback callback) {
    lefuScale.checkDeviceConnection(callback);
  }

  public void getLefuDevices(ResultCallback callback) {
    lefuScale.getDevices(callback);
  }

  public void syncLefuUserInfo(Integer age, Double height, String sex, ResultCallback callback) {
    lefuScale.syncUserInfo(age, height, sex, callback);
  }

  public void startLefuMeasurement(Activity activity, ResultCallback callback) {
    lefuScale.startMeasurement(activity, callback);
  }

  public void startLefuMeasurement(Activity activity, MeasurementInput input, ResultCallback callback) {
    lefuScale.startMeasurement(activity, input, callback);
  }

  public void initializeIceLefu(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startIceLefuScan(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void stopIceLefuScan(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void connectIceLefuDevice(String deviceAddress, String deviceName, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void checkIceLefuConnection(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void getIceLefuDevices(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void syncIceLefuUserInfo(Integer age, Double height, String sex, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startIceLefuMeasurement(Activity activity, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startIceLefuMeasurement(Activity activity, MeasurementInput input, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void initializeJambul(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startJambulScan(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void stopJambulScan(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void stopJambul(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void checkJambulConnection(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void getJambulDevices(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void syncJambulUserInfo(Integer age, Double height, String sex, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startJambulMeasurement(Activity activity, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void startJambulMeasurement(Activity activity, MeasurementInput input, ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }

  public void removeJambulConnectedDevice(ResultCallback callback) {
    rejectUnsupportedFlow(callback);
  }
}
