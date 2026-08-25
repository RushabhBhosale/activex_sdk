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
import com.lefu.ppcalculate.vo.PPBodyDetailModel;
import com.peng.ppscale.PPBluetoothKit;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.device.PeripheralJambul.PPBlutoothPeripheralJambulController;
import com.peng.ppscale.search.PPSearchManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class JambulLefuPlugin {
  private static final String TAG = "JambulLefuPlugin";

  private final Context context;
  private LefuEventListener eventListener;
  private PPSearchManager ppScale;
  private final List<PPDeviceModel> foundDevices = new ArrayList<>();
  private PPBlutoothPeripheralJambulController jambulController;

  private boolean isJambulSessionRunning = false;
  private PPDeviceModel selectedDevice = null;

  private String calculateType = "";
  private Integer age = 0;
  private String gender = "";
  private Double height = 0.0;
  private PPUserModel userModel;

  private PPDataChangeListener jambulDataChangeListener = null;

  public JambulLefuPlugin(Context context) {
    this.context = context.getApplicationContext();
  }

  public void setEventListener(LefuEventListener eventListener) {
    this.eventListener = eventListener;
  }

  private void notifyListeners(String eventName, ResultData data) {
    if (eventListener != null) eventListener.onEvent(eventName, data);
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

  private void notifyDeviceInfo(ResultData data) {
    if (eventListener != null) eventListener.onDeviceInfo(data);
  }

  private void resolve(ResultCallback callback) {
    if (callback != null) callback.onSuccess(new ResultData());
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

  public void initializeSDK(ResultCallback callback) {
    String appKey = "lefu0c091646522ebd05";
    String appSecret = "7MIs7ILShqp84endnTlTJMFG59iVB0BuejBWlhgq7+E=";

    Log.d(TAG, "Initializing SDK...");
    PPBluetoothKit.INSTANCE.initSdk(context, appKey, appSecret, "lefu.config");
    PPCalculateKit.INSTANCE.initSdk(context);
    Log.d(TAG, "SDK initialized");
    resolve(callback);
  }

  private void ensureJambulListener() {
    if (jambulDataChangeListener != null) return;

    jambulDataChangeListener = new PPDataChangeListener() {
      @Override
      public void monitorProcessData(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        if (body == null) return;

        ResultData evt = new ResultData();
        evt.putValue("event", "measurementStarted");
        notifyListeners("measurementUpdate", evt);

        Log.d(TAG, "Jambul process weightKg=" + body.getPpWeightKg());
      }

      @Override
      public void monitorLockData(@Nullable PPBodyBaseModel bodyBaseModel, @Nullable PPDeviceModel deviceModel) {
        if (bodyBaseModel == null || deviceModel == null) return;

        Log.d(TAG, "Jambul lock received");

        ResultData measurementComplete = new ResultData();
        measurementComplete.putValue("event", "measurementComplete");
        notifyListeners("measurementUpdate", measurementComplete);
      }

      @Override
      public void monitorDataFail(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        ResultData evt = new ResultData();
        evt.putValue("event", "dataFailure");
        evt.putValue("message", "Jambul data fail");
        notifyListeners("measurementUpdate", evt);
        Log.e(TAG, "Jambul data fail");
      }

      @Override
      public void monitorOverWeight() {
        ResultData evt = new ResultData();
        evt.putValue("event", "overWeight");
        notifyListeners("measurementUpdate", evt);
      }

      @Override public void onImpedanceFatting() {}
      @Override public void onDeviceShutdown() {}
      @Override public void monitorScaleState(@Nullable PPScaleState state) {
        if (state == null) return;

        ResultData evt = new ResultData();
        evt.putValue("event", "scaleState");
        evt.putValue("state", state.toString());
        notifyListeners("measurementUpdate", evt);
      }
      @Override public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo vo) {}
      @Override public void monitorFootLenMeasure(PPScaleFootState state, int i) {}
    };
  }

  private void startJambulSession(PPDeviceModel model) {
    if (jambulController == null) {
      jambulController = new PPBlutoothPeripheralJambulController();
    }

    ensureJambulListener();
    jambulController.registDataChangeListener(jambulDataChangeListener);

    String mac = model.getDeviceMac();
    Log.d(TAG, "Jambul startSearch mac=" + mac);
    notifyConnectionState("connecting", "Starting a Jambul session with the discovered scale...", model);

    jambulController.startSearch(mac, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel deviceModel) {
        Log.d(TAG, "Jambul workState=" + state);
        PPDeviceModel reportedDevice = deviceModel != null ? deviceModel : model;
        switch (state) {
          case PPBleWorkStateConnected:
            isJambulSessionRunning = true;
            selectedDevice = reportedDevice;
            notifyConnectionState("connected", "Jambul scale connected successfully.", reportedDevice);
            ResultData connected = new ResultData();
            connected.putValue("deviceName", reportedDevice.getDeviceName());
            connected.putValue("deviceAddress", reportedDevice.getDeviceMac());
            notifyDeviceInfo(connected);
            break;
          case PPBleWorkStateConnectFailed:
            isJambulSessionRunning = false;
            selectedDevice = null;
            notifyConnectionState("failed", "Could not connect to the discovered Jambul scale.", reportedDevice);
            break;
          case PPBleWorkStateDisconnected:
            isJambulSessionRunning = false;
            selectedDevice = null;
            notifyConnectionState("disconnected", "The Jambul scale disconnected.", reportedDevice);
            break;
          default:
            notifyConnectionState("state", "Jambul connection state: " + state, reportedDevice);
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState sw) {
        Log.d(TAG, "Bluetooth switch=" + sw);
        if (sw == PPBleSwitchState.PPBleSwitchStateOff) {
          notifyConnectionState("failed", "Bluetooth is turned off.", model);
        }
      }
    });

    isJambulSessionRunning = true;
    selectedDevice = model;

    ResultData evt = new ResultData();
    evt.putValue("status", "session_started");
    evt.putValue("deviceName", model.getDeviceName());
    evt.putValue("deviceAddress", model.getDeviceMac());
    notifyListeners("deviceConnectionChanged", evt);
  }

  public void startScan(ResultCallback callback) {
    if (ppScale == null) {
      ppScale = PPSearchManager.getInstance();
    }

    foundDevices.clear();
    Set<String> macSet = new HashSet<>();
    AtomicBoolean resolved = new AtomicBoolean(false);

    ppScale.startSearchDeviceList(300000, new PPSearchDeviceInfoInterface() {
      @Override
      public void onSearchDevice(PPDeviceModel model, String data) {
        if (model == null || model.getDeviceMac() == null) return;
        if (macSet.contains(model.getDeviceMac())) return;

        macSet.add(model.getDeviceMac());
        foundDevices.add(model);

        calculateType = String.valueOf(model.getDeviceCalcuteType());
        Log.d(TAG, "Found device: " + model.toString());

        startJambulSession(model);

        ResultData result = new ResultData();
        result.putValue("connected", false);
        result.putValue("state", "discovered");
        result.putValue("message", "Scale discovered; starting a Jambul connection session.");
        result.putValue("deviceName", model.getDeviceName());
        result.putValue("deviceAddress", model.getDeviceMac());
        notifyListeners("lefuDeviceInfo", result);

        if (resolved.compareAndSet(false, true)) {
          resolve(callback, result);
        }
      }
    }, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel deviceModel) {
        Log.d(TAG, "Scan state: " + state);
        switch (state) {
          case PPBleWorkStateSearching:
            notifyScanState("searching", "Scanning for Jambul scales...");
            break;
          case PPBleWorkSearchTimeOut:
            notifyScanState("timeout", "No scale was found before the scan timed out.");
            break;
          case PPBleWorkSearchFail:
            notifyScanState("failed", "The Bluetooth scan failed.");
            break;
          case PPBleStateSearchCanceled:
            notifyScanState("cancelled", "The Bluetooth scan was cancelled.");
            break;
          default:
            notifyScanState("state", "Bluetooth scan state: " + state);
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState sw) {
        if (sw == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is OFF");
          notifyScanState("failed", "Bluetooth is turned off.");
        }
      }
    });

    ResultData result = new ResultData();
    result.putValue("connected", false);
    result.putValue("state", "searching");
    result.putValue("message", "Scanning started. Waiting for devices...");
    notifyListeners("lefuDeviceInfo", result);
    if (resolved.compareAndSet(false, true)) {
      resolve(callback, result);
    }
  }

  public void stopScan(ResultCallback callback) {
    if (ppScale != null) {
      try { ppScale.stopSearch(); } catch (Exception ignore) {}
      Log.d(TAG, "Scan stopped");
    }
    resolve(callback);
  }

  public void stopJambul(ResultCallback callback) {
    if (jambulController != null) {
      try { jambulController.registDataChangeListener(null); } catch (Exception ignore) {}
      try { jambulController.stopSeach(); } catch (Exception ignore) {}
    }

    isJambulSessionRunning = false;
    selectedDevice = null;

    ResultData evt = new ResultData();
    evt.putValue("status", "session_stopped");
    notifyListeners("deviceConnectionChanged", evt);

    resolve(callback);
  }

  public void getDevices(ResultCallback callback) {
    if (foundDevices.isEmpty()) {
      reject(callback, "No devices found.");
      return;
    }

    ResultData result = new ResultData();
    List<ResultData> list = new ArrayList<>();

    for (PPDeviceModel d : foundDevices) {
      ResultData o = new ResultData();
      o.putValue("deviceName", d.getDeviceName());
      o.putValue("deviceAddress", d.getDeviceMac());
      list.add(o);
    }

    result.putValue("devices", list);
    resolve(callback, result);
  }

  public void checkDeviceConnection(ResultCallback callback) {
    ResultData result = new ResultData();
    result.putValue("isConnected", isJambulSessionRunning);

    if (isJambulSessionRunning && selectedDevice != null) {
      result.putValue("deviceName", selectedDevice.getDeviceName());
      result.putValue("deviceAddress", selectedDevice.getDeviceMac());
    }

    resolve(callback, result);
  }

  public void syncUserInfo(Integer age, Double height, String sexValue, ResultCallback callback) {
    if (age == null || height == null || sexValue == null) {
      reject(callback, "Missing required user information: age, height, or sex.");
      return;
    }

    this.age = age;
    this.height = height;
    gender = sexValue.toLowerCase();

    PPUserGender sex = "male".equals(gender)
      ? PPUserGender.PPUserGenderMale
      : PPUserGender.PPUserGenderFemale;

    userModel = new PPUserModel.Builder()
      .setSex(sex)
      .setHeight(this.height.intValue())
      .setAge(this.age)
      .build();

    Log.d(TAG, "User info synced: age=" + this.age + " height=" + this.height + " sex=" + gender);
    resolve(callback);
  }

  public void startMeasurement(Activity activity, ResultCallback callback) {
    startMeasurement(activity, null, callback);
  }

  public void startMeasurement(Activity activity, MeasurementInput input, ResultCallback callback) {
    String inputError = input == null ? "Measurement input is required." : input.validationError();
    if (inputError != null) {
      reject(callback, inputError);
      return;
    }

    if (activity != null) {
      activity.runOnUiThread(() ->
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      );
    }

    if (!isJambulSessionRunning || selectedDevice == null) {
      reject(callback, "No Jambul session running. Start scan first.");
      return;
    }

    if (userModel == null) {
      reject(callback, "User info missing. Call syncUserInfo first.");
      return;
    }

    if (jambulController == null) {
      reject(callback, "Jambul controller not initialized.");
      return;
    }

    final AtomicBoolean resolved = new AtomicBoolean(false);

    PPDataChangeListener oneShotListener = new PPDataChangeListener() {
      @Override
      public void monitorProcessData(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        if (jambulDataChangeListener != null) {
          jambulDataChangeListener.monitorProcessData(body, dev);
        }
      }

      @Override
      public void onImpedanceFatting() {
        ResultData evt = new ResultData();
        evt.putValue("event", "bodyFatMeasurementStarted");
        notifyListeners("measurementUpdate", evt);

        if (jambulDataChangeListener != null) {
          jambulDataChangeListener.onImpedanceFatting();
        }
      }

      @Override
      public void monitorLockData(@Nullable PPBodyBaseModel bodyBaseModel, @Nullable PPDeviceModel deviceModel) {
        if (jambulDataChangeListener != null) {
          jambulDataChangeListener.monitorLockData(bodyBaseModel, deviceModel);
        }

        if (resolved.get()) return;
        if (bodyBaseModel == null || deviceModel == null) return;

        try {
          PPScaleDefine.PPDeviceCalcuteType ct;
          try {
            ct = PPScaleDefine.PPDeviceCalcuteType.valueOf(calculateType);
            Log.d("Clla", "Calculate Type" + ct);
          } catch (Exception e) {
            ct = deviceModel.getDeviceCalcuteType();
          }

          String weightStr = PPUtil.getWeightValueD(
            bodyBaseModel.getUnit(),
            bodyBaseModel.getPpWeightKg(),
            deviceModel.getDeviceAccuracyType().getType(),
            true
          );

          double weightKg = bodyBaseModel.getPpWeightKg();
          int weightInCents = (int) Math.round(weightKg * 100.0);
          bodyBaseModel.setWeight(weightInCents);
          bodyBaseModel.setUserModel(userModel);
          bodyBaseModel.setSecret(SecretManager.getSecret(ct.getType()));
          deviceModel.setDeviceCalcuteType(ct);

          PPBodyFatModel fatModel = new PPBodyFatModel(bodyBaseModel, bodyBaseModel);

          ResultData fieldsData = new ResultData();
          Field[] fields = fatModel.getClass().getDeclaredFields();
          for (Field field : fields) {
            try {
              field.setAccessible(true);
              Object v = field.get(fatModel);
              String publicFieldName = FieldKeyNormalizer.toPublicKey(field.getName());
              fieldsData.putValue(publicFieldName, FieldKeyNormalizer.toPublicValue(v));
            } catch (IllegalAccessException ignore) {}
          }

          ResultData result = new ResultData();
          result.putValue("fields", fieldsData);

          PPBodyDetailModel detail = new PPBodyDetailModel(fatModel);
          Log.d(TAG, "Body detail: " + FieldKeyNormalizer.toPublicValue(detail));

          if (!resolved.compareAndSet(false, true)) return;
          ensureJambulListener();
          jambulController.registDataChangeListener(jambulDataChangeListener);

          if (activity != null) {
            activity.runOnUiThread(() ->
              activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            );
          }

          new MeasurementValidationClient().validate(input, result, new MeasurementValidationClient.Callback() {
            @Override
            public void onAuthorized(ResultData response) {
              resolve(callback, response);
            }

            @Override
            public void onRejected(String message, Throwable error) {
              ResultData validationFailed = new ResultData();
              validationFailed.putValue("event", "measurementAuthorizationFailed");
              validationFailed.putValue("message", message);
              notifyListeners("measurementUpdate", validationFailed);
              reject(callback, message, error);
            }
          });
        } catch (Exception e) {
          Log.e(TAG, "Calculation failed", e);

          resolved.set(true);
          ensureJambulListener();
          jambulController.registDataChangeListener(jambulDataChangeListener);

          if (activity != null) {
            activity.runOnUiThread(() ->
              activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            );
          }

          reject(callback, "Calculation failed: " + e.getMessage(), e);
        }
      }

      @Override
      public void monitorDataFail(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        if (jambulDataChangeListener != null) {
          jambulDataChangeListener.monitorDataFail(body, dev);
        }

        if (resolved.get()) return;
        resolved.set(true);

        ensureJambulListener();
        jambulController.registDataChangeListener(jambulDataChangeListener);

        if (activity != null) {
          activity.runOnUiThread(() ->
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          );
        }

        reject(callback, "Jambul data fail");
      }

      @Override public void monitorOverWeight() {
        if (jambulDataChangeListener != null) jambulDataChangeListener.monitorOverWeight();
      }

      @Override public void onDeviceShutdown() {
        if (jambulDataChangeListener != null) jambulDataChangeListener.onDeviceShutdown();
      }

      @Override public void monitorScaleState(@Nullable PPScaleState state) {
        if (jambulDataChangeListener != null) jambulDataChangeListener.monitorScaleState(state);
      }

      @Override public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo vo) {
        if (jambulDataChangeListener != null) jambulDataChangeListener.monitorLockDataByCalculateInScale(vo);
      }

      @Override public void monitorFootLenMeasure(PPScaleFootState state, int i) {
        if (jambulDataChangeListener != null) jambulDataChangeListener.monitorFootLenMeasure(state, i);
      }
    };

    jambulController.registDataChangeListener(oneShotListener);

    ResultData evt = new ResultData();
    evt.putValue("event", "measurementStarted");
    notifyListeners("measurementUpdate", evt);
  }

  public void removeConnectedDevice(ResultCallback callback) {
    try {
      Log.d(TAG, "removeConnectedDevice called");

      if (jambulController != null) {
        try { jambulController.registDataChangeListener(null); } catch (Exception ignore) {}
        try { jambulController.stopSeach(); } catch (Exception ignore) {}
      }

      if (ppScale != null) {
        try { ppScale.stopSearch(); } catch (Exception ignore) {}
      }

      isJambulSessionRunning = false;
      selectedDevice = null;
      calculateType = "";

      ResultData evt = new ResultData();
      evt.putValue("status", "session_stopped");
      notifyListeners("deviceConnectionChanged", evt);

      ResultData res = new ResultData();
      res.putValue("ok", true);
      res.putValue("message", "Session stopped");
      resolve(callback, res);
    } catch (Exception e) {
      Log.e(TAG, "removeConnectedDevice failed (ignored)", e);
      ResultData res = new ResultData();
      res.putValue("ok", true);
      res.putValue("warning", String.valueOf(e.getMessage()));
      resolve(callback, res);
    }
  }
}
