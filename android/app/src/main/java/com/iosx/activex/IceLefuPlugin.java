package com.iosx.activex;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.lefu.ppbase.PPBodyBaseModel;
import com.lefu.ppbase.PPDeviceModel;
import com.lefu.ppbase.PPScaleDefine;
import com.lefu.ppbase.util.PPUtil;
import com.lefu.ppbase.vo.PPBodyFatInScaleVo;
import com.lefu.ppbase.vo.PPScaleFootState;
import com.lefu.ppbase.vo.PPScaleState;
import com.lefu.ppbase.vo.PPUserGender;
import com.lefu.ppbase.vo.PPUserModel;
import com.lefu.ppcalculate.PPBodyFatModel;
import com.lefu.ppcalculate.PPCalculateKit;
import com.peng.ppscale.PPBluetoothKit;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.device.PeripheralIce.PPBlutoothPeripheralIceController;
import com.peng.ppscale.search.PPSearchManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated implementation for PPBlutoothPeripheralIceController scales. */
public final class IceLefuPlugin {
  private static final String TAG = "IceLefuPlugin";

  private final Context context;
  private LefuEventListener eventListener;
  private PPSearchManager ppScale;
  private final List<PPDeviceModel> foundDevices = new ArrayList<>();
  private final Set<String> connectionAttempts = new HashSet<>();
  private PPBlutoothPeripheralIceController controller;
  private boolean isDeviceConnected;
  private boolean measurementActive;
  private PPDeviceModel connectedDevice;
  private String calculateType = "";
  private PPUserModel userModel;

  public IceLefuPlugin(Context context) {
    this.context = context.getApplicationContext();
  }

  public void setEventListener(LefuEventListener eventListener) {
    this.eventListener = eventListener;
  }

  public void initializeSDK(ResultCallback callback) {
    PPBluetoothKit.INSTANCE.initSdk(
      context,
      "lefu0c091646522ebd05",
      "7MIs7ILShqp84endnTlTJMFG59iVB0BuejBWlhgq7+E=",
      "lefu.config"
    );
    PPCalculateKit.INSTANCE.initSdk(context);
    resolve(callback);
  }

  public void startScan(ResultCallback callback) {
    if (ppScale == null) ppScale = PPSearchManager.getInstance();

    foundDevices.clear();
    connectionAttempts.clear();
    Set<String> vendorAddresses = new HashSet<>();
    AtomicBoolean resolved = new AtomicBoolean(false);

    ppScale.startSearchDeviceList(300000, new PPSearchDeviceInfoInterface() {
      @Override
      public void onSearchDevice(PPDeviceModel device, String data) {
        if (device == null || device.getDeviceMac() == null || !vendorAddresses.add(device.getDeviceMac())) {
          return;
        }

        addDiscoveredDevice(device);
        connectToDevice(device, null);
        ResultData result = deviceResult(device, "discovered", "Ice Lefu scale discovered; connecting.");
        notifyListeners("lefuDeviceInfo", result);
        if (resolved.compareAndSet(false, true)) resolve(callback, result);
      }
    }, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel device) {
        switch (state) {
          case PPBleWorkStateSearching:
            notifyScanState("searching", "Scanning for Ice Lefu scales...");
            break;
          case PPBleWorkSearchTimeOut:
            notifyScanState("timeout", "No Ice Lefu scale was found before the scan timed out.");
            break;
          case PPBleWorkSearchFail:
            notifyScanState("failed", "The Ice Lefu scan failed.");
            break;
          case PPBleStateSearchCanceled:
            notifyScanState("cancelled", "The Ice Lefu scan was cancelled.");
            break;
          default:
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState state) {
        if (state == PPBleSwitchState.PPBleSwitchStateOff) {
          notifyScanState("failed", "Bluetooth is turned off.");
        }
      }
    });

    ResultData result = new ResultData();
    result.putValue("connected", false);
    result.putValue("state", "searching");
    result.putValue("message", "Scanning started. Waiting for an Ice Lefu scale...");
    notifyListeners("lefuDeviceInfo", result);
    if (resolved.compareAndSet(false, true)) resolve(callback, result);
  }

  public void stopScan(ResultCallback callback) {
    if (ppScale != null) {
      try {
        ppScale.stopSearch();
      } catch (Exception error) {
        Log.w(TAG, "Could not stop the Ice Lefu vendor scan.", error);
      }
    }
    resolve(callback);
  }

  public void connectToDevice(String address, String name, ResultCallback callback) {
    if (address == null || address.isEmpty()) {
      reject(callback, "Device address is missing.");
      return;
    }
    if (name == null || name.isEmpty()) {
      reject(callback, "Device name is missing.");
      return;
    }
    PPDeviceModel device = new PPDeviceModel(address, name);
    addDiscoveredDevice(device);
    connectToDevice(device, callback);
  }

  public void checkDeviceConnection(ResultCallback callback) {
    ResultData result = new ResultData();
    result.putValue("isConnected", isDeviceConnected);
    if (isDeviceConnected && connectedDevice != null) {
      result.putValue("deviceName", connectedDevice.getDeviceName());
      result.putValue("deviceAddress", connectedDevice.getDeviceMac());
    }
    resolve(callback, result);
  }

  public void getDevices(ResultCallback callback) {
    if (foundDevices.isEmpty()) {
      reject(callback, "No Ice Lefu devices found.");
      return;
    }
    List<ResultData> devices = new ArrayList<>();
    for (PPDeviceModel device : foundDevices) {
      devices.add(deviceResult(device, "discovered", ""));
    }
    ResultData result = new ResultData();
    result.putValue("devices", devices);
    resolve(callback, result);
  }

  public void syncUserInfo(Integer age, Double height, String sexValue, ResultCallback callback) {
    if (age == null || height == null || sexValue == null) {
      reject(callback, "Missing required user information: age, height, or sex.");
      return;
    }
    PPUserGender sex = "male".equalsIgnoreCase(sexValue)
      ? PPUserGender.PPUserGenderMale
      : PPUserGender.PPUserGenderFemale;
    userModel = new PPUserModel.Builder()
      .setSex(sex)
      .setHeight(height.intValue())
      .setAge(age)
      .build();
    resolve(callback);
  }

  public void startMeasurement(Activity activity, ResultCallback callback) {
    startMeasurement(activity, null, callback);
  }

  public void startMeasurement(Activity activity, MeasurementInput input, ResultCallback callback) {
    if (activity == null) {
      reject(callback, "Activity is required to start measurement.");
      return;
    }
    String inputError = input == null ? "Measurement input is required." : input.validationError();
    if (inputError != null) {
      reject(callback, inputError);
      return;
    }
    if (!isDeviceConnected || controller == null) {
      reject(callback, "No connected Ice Lefu scale. Scan and wait for the connection to complete first.");
      return;
    }
    if (userModel == null) {
      reject(callback, "User profile is missing. Sync age, height, and sex before starting a measurement.");
      return;
    }

    activity.runOnUiThread(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    measurementActive = true;
    controller.startKeepAlive();
    AtomicBoolean measurementResolved = new AtomicBoolean(false);

    // Ice scales stream after connection; unlike normal Lefu/Torre, there is no startMeasure command.
    controller.registDataChangeListener(new PPDataChangeListener() {
      @Override public void monitorFootLenMeasure(PPScaleFootState state, int value) {}

      @Override public void onImpedanceFatting() {
        measurementEvent("bodyFatMeasurementStarted");
      }

      @Override public void onDeviceShutdown() {
        if (measurementActive) {
          measurementActive = false;
          measurementEvent("measurementStop");
        }
      }

      @Override public void monitorScaleState(@Nullable PPScaleState state) {
        if (state == null) return;
        ResultData event = new ResultData();
        event.putValue("event", "scaleState");
        event.putValue("state", state.toString());
        notifyListeners("measurementUpdate", event);
      }

      @Override public void monitorProcessData(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel device) {
        if (body != null) measurementEvent("measurementStarted");
      }

      @Override public void monitorOverWeight() {
        measurementEvent("overWeight");
      }

      @Override public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo body) {}

      @Override public void monitorLockData(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel device) {
        if (body == null || device == null) return;
        if (body.isHeartRating()) {
          measurementEvent("heartRateMeasuring");
          return;
        }
        if (!measurementResolved.compareAndSet(false, true)) return;
        submitMeasurement(activity, input, callback, body, device);
      }

      @Override public void monitorDataFail(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel device) {
        if (measurementResolved.get()) return;
        measurementActive = false;
        ResultData event = new ResultData();
        event.putValue("event", "dataFailure");
        event.putValue("message", "Ice Lefu measurement data failed.");
        notifyListeners("measurementUpdate", event);
      }
    });
  }

  private void connectToDevice(PPDeviceModel device, ResultCallback callback) {
    String address = device.getDeviceMac();
    if (address == null || !connectionAttempts.add(address)) {
      if (callback != null) reject(callback, "A connection to this Ice Lefu scale is already in progress.");
      return;
    }
    if (controller == null) controller = new PPBlutoothPeripheralIceController();

    notifyConnectionState("connecting", "Connecting to the Ice Lefu scale...", device);
    controller.startConnect(device, new PPBleStateInterface() {
      @Override public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel reportedDevice) {
        PPDeviceModel activeDevice = reportedDevice != null ? reportedDevice : device;
        switch (state) {
          case PPBleWorkStateConnected:
            isDeviceConnected = true;
            connectedDevice = activeDevice;
            finishScanningAfterConnection();
            controller.startKeepAlive();
            notifyConnectionState("connected", "Ice Lefu scale connected successfully.", activeDevice);
            notifyDeviceInfo(activeDevice);
            resolve(callback, deviceResult(activeDevice, "connected", "Ice Lefu scale connected successfully."));
            break;
          case PPBleWorkStateConnectFailed:
            connectionAttempts.remove(address);
            isDeviceConnected = false;
            connectedDevice = null;
            controller.stopKeepAlive();
            notifyConnectionState("failed", "Could not connect to the Ice Lefu scale.", activeDevice);
            reject(callback, "Connection failed");
            break;
          case PPBleWorkStateDisconnected:
            isDeviceConnected = false;
            connectedDevice = null;
            controller.stopKeepAlive();
            notifyConnectionState("disconnected", "The Ice Lefu scale disconnected.", activeDevice);
            ResultData disconnected = new ResultData();
            disconnected.putValue("message", "Ice Lefu scale disconnected");
            notifyListeners("deviceDisconnected", disconnected);
            if (measurementActive) {
              measurementActive = false;
              measurementEvent("measurementStop");
            }
            break;
          default:
            break;
        }
      }

      @Override public void monitorBluetoothSwitchState(PPBleSwitchState state) {
        if (state == PPBleSwitchState.PPBleSwitchStateOff) {
          notifyConnectionState("failed", "Bluetooth is turned off.", device);
        }
      }
    });
  }

  private void submitMeasurement(
    Activity activity,
    MeasurementInput input,
    ResultCallback callback,
    PPBodyBaseModel body,
    PPDeviceModel scaleDevice
  ) {
    activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    measurementActive = false;
    measurementEvent("measurementComplete");

    String weightValue = PPUtil.getWeightValueD(
      body.getUnit(), body.getPpWeightKg(), scaleDevice.getDeviceAccuracyType().getType(), true
    );
    final double weight;
    try {
      weight = Double.parseDouble(weightValue);
    } catch (NumberFormatException error) {
      reject(callback, "The Ice Lefu scale returned an invalid weight.", error);
      return;
    }

    PPDeviceModel calculationDevice = new PPDeviceModel("", deviceName(scaleDevice));
    calculationDevice.setDeviceCalcuteType(resolveCalculateType(scaleDevice));
    body.setWeight((int) Math.round(weight * 100.0));
    body.setUserModel(userModel);
    body.setSecret(SecretManager.getSecret(calculationDevice.getDeviceCalcuteType().getType()));

    PPBodyFatModel fatModel = new PPBodyFatModel(body, body);
    ResultData measurement = new ResultData();
    ResultData fields = new ResultData();
    for (Field field : fatModel.getClass().getDeclaredFields()) {
      try {
        field.setAccessible(true);
        fields.putValue(
          FieldKeyNormalizer.toPublicKey(field.getName()),
          FieldKeyNormalizer.toPublicValue(field.get(fatModel))
        );
      } catch (IllegalAccessException error) {
        Log.w(TAG, "Could not read calculated Ice Lefu field " + field.getName(), error);
      }
    }
    measurement.putValue("fields", fields);

    new MeasurementValidationClient().validate(input, measurement, new MeasurementValidationClient.Callback() {
      @Override public void onAuthorized(ResultData response) {
        resolve(callback, response);
      }

      @Override public void onRejected(String message, Throwable error) {
        ResultData event = new ResultData();
        event.putValue("event", "measurementAuthorizationFailed");
        event.putValue("message", message);
        notifyListeners("measurementUpdate", event);
        reject(callback, message, error);
      }
    });
  }

  private void addDiscoveredDevice(PPDeviceModel device) {
    if (device == null) return;
    String address = device.getDeviceMac();
    for (PPDeviceModel existing : foundDevices) {
      if (address != null && address.equals(existing.getDeviceMac())) return;
    }
    foundDevices.add(device);
    if (device.getDeviceCalcuteType() != null) {
      calculateType = device.getDeviceCalcuteType().toString();
    }
  }

  private ResultData deviceResult(PPDeviceModel device, String state, String message) {
    ResultData result = new ResultData();
    result.putValue("connected", isDeviceConnected);
    result.putValue("state", state);
    result.putValue("message", message);
    if (device != null) {
      result.putValue("deviceName", device.getDeviceName());
      result.putValue("deviceAddress", device.getDeviceMac());
    }
    return result;
  }

  private String deviceName(PPDeviceModel scaleDevice) {
    if (connectedDevice != null && connectedDevice.getDeviceName() != null) {
      return connectedDevice.getDeviceName();
    }
    return scaleDevice.getDeviceName() == null ? "Ice Lefu" : scaleDevice.getDeviceName();
  }

  private PPScaleDefine.PPDeviceCalcuteType resolveCalculateType(PPDeviceModel scaleDevice) {
    if (calculateType != null && !calculateType.isEmpty()) {
      try {
        return PPScaleDefine.PPDeviceCalcuteType.valueOf(calculateType);
      } catch (IllegalArgumentException ignored) {
        // Fall through to the device's calculation type.
      }
    }
    if (scaleDevice.getDeviceCalcuteType() != null) return scaleDevice.getDeviceCalcuteType();
    if (connectedDevice != null && connectedDevice.getDeviceCalcuteType() != null) {
      return connectedDevice.getDeviceCalcuteType();
    }
    return PPScaleDefine.PPDeviceCalcuteType.PPDeviceCalcuteTypeAlternate;
  }

  private void finishScanningAfterConnection() {
    if (ppScale != null) {
      try {
        ppScale.stopSearch();
      } catch (Exception error) {
        Log.w(TAG, "Could not stop scanning after Ice Lefu connected.", error);
      }
    }
  }

  private void measurementEvent(String eventName) {
    ResultData event = new ResultData();
    event.putValue("event", eventName);
    notifyListeners("measurementUpdate", event);
  }

  private void notifyListeners(String eventName, ResultData data) {
    if (eventListener != null) eventListener.onEvent(eventName, data);
  }

  private void notifyDeviceInfo(PPDeviceModel device) {
    if (eventListener == null || device == null) return;
    ResultData result = new ResultData();
    result.putValue("deviceName", device.getDeviceName());
    result.putValue("deviceAddress", device.getDeviceMac());
    eventListener.onDeviceInfo(result);
  }

  private void notifyScanState(String state, String message) {
    ResultData result = new ResultData();
    result.putValue("state", state);
    result.putValue("message", message);
    notifyListeners("scanState", result);
  }

  private void notifyConnectionState(String state, String message, PPDeviceModel device) {
    ResultData result = new ResultData();
    result.putValue("state", state);
    result.putValue("message", message);
    if (device != null) {
      result.putValue("deviceName", device.getDeviceName());
      result.putValue("deviceAddress", device.getDeviceMac());
    }
    notifyListeners("connectionState", result);
  }

  private void resolve(ResultCallback callback) {
    resolve(callback, new ResultData());
  }

  private void resolve(ResultCallback callback, ResultData data) {
    if (callback != null) callback.onSuccess(data);
  }

  private void reject(ResultCallback callback, String message) {
    reject(callback, message, null);
  }

  private void reject(ResultCallback callback, String message, Throwable error) {
    if (callback != null) callback.onError(message, error);
  }
}
