package com.iosx.activex;

import android.app.Activity;
import android.content.Context;

public class ActiveXScaleSDK {
  private final LefuPlugin lefuScale;
  private final JambulLefuPlugin jambulScale;

  public ActiveXScaleSDK(Context context) {
    lefuScale = new LefuPlugin(context);
    jambulScale = new JambulLefuPlugin(context);
  }

  public void setEventListener(LefuEventListener eventListener) {
    lefuScale.setEventListener(eventListener);
    jambulScale.setEventListener(eventListener);
  }

  public LefuPlugin getLefuScale() {
    return lefuScale;
  }

  public JambulLefuPlugin getJambulScale() {
    return jambulScale;
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

  public void initializeJambul(ResultCallback callback) {
    jambulScale.initializeSDK(callback);
  }

  public void startJambulScan(ResultCallback callback) {
    jambulScale.startScan(callback);
  }

  public void stopJambulScan(ResultCallback callback) {
    jambulScale.stopScan(callback);
  }

  public void stopJambul(ResultCallback callback) {
    jambulScale.stopJambul(callback);
  }

  public void checkJambulConnection(ResultCallback callback) {
    jambulScale.checkDeviceConnection(callback);
  }

  public void getJambulDevices(ResultCallback callback) {
    jambulScale.getDevices(callback);
  }

  public void syncJambulUserInfo(Integer age, Double height, String sex, ResultCallback callback) {
    jambulScale.syncUserInfo(age, height, sex, callback);
  }

  public void startJambulMeasurement(Activity activity, ResultCallback callback) {
    jambulScale.startMeasurement(activity, callback);
  }

  public void removeJambulConnectedDevice(ResultCallback callback) {
    jambulScale.removeConnectedDevice(callback);
  }
}
