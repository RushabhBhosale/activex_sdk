package com.iosx.activex;

import static com.iosx.activex.ICPlugin.notifyDeviceInfo;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
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
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.device.PeripheralJambul.PPBlutoothPeripheralJambulController;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.search.PPSearchManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "JambulLefuPlugin")
public class JambulLefuPlugin extends Plugin {

  private static final String TAG = "JambulLefuPlugin";

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

  @PluginMethod
  public void initializeSDK(PluginCall call) {
    String appKey = "lefu0c091646522ebd05";
    String appSecret = "7MIs7ILShqp84endnTlTJMFG59iVB0BuejBWlhgq7+E=";

    Log.d(TAG, "Initializing SDK...");

    Context context = getContext();
    PPBluetoothKit.INSTANCE.initSdk(context, appKey, appSecret, "lefu.config");
    PPCalculateKit.INSTANCE.initSdk(context);

    Log.d(TAG, "SDK initialized");
    call.resolve();
  }

  private void ensureJambulListener() {
    if (jambulDataChangeListener != null) return;

    jambulDataChangeListener = new PPDataChangeListener() {

      @Override
      public void monitorProcessData(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        if (body == null) return;

        JSObject evt = new JSObject();
        evt.put("event", "measurementStarted");
        evt.put("weight", body.getWeight());
        evt.put("weightKg", body.getPpWeightKg());
        notifyListeners("measurementUpdate", evt);

        Log.d(TAG, "Jambul process weightKg=" + body.getPpWeightKg());
      }

      @Override
      public void monitorLockData(@Nullable PPBodyBaseModel bodyBaseModel, @Nullable PPDeviceModel deviceModel) {
        if (bodyBaseModel == null || deviceModel == null) return;

        Log.d(TAG, "Jambul lock received");

        JSObject measurementComplete = new JSObject();
        measurementComplete.put("event", "measurementComplete");
        measurementComplete.put("weightKg", bodyBaseModel.getPpWeightKg());
        notifyListeners("measurementUpdate", measurementComplete);

        // NOTE: This listener does NOT resolve PluginCall.
        // Actual "fields" result is returned by one-shot listener inside startMeasurement.
      }

      @Override
      public void monitorDataFail(@Nullable PPBodyBaseModel body, @Nullable PPDeviceModel dev) {
        JSObject evt = new JSObject();
        evt.put("event", "dataFailure");
        evt.put("message", "Jambul data fail");
        notifyListeners("measurementUpdate", evt);
        Log.e(TAG, "Jambul data fail");
      }

      @Override
      public void monitorOverWeight() {
        JSObject evt = new JSObject();
        evt.put("event", "overWeight");
        notifyListeners("measurementUpdate", evt);
      }

      @Override public void onImpedanceFatting() { }
      @Override public void onDeviceShutdown() { }
      @Override public void monitorScaleState(@Nullable PPScaleState state) { }
      @Override public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo vo) { }
      @Override public void monitorFootLenMeasure(PPScaleFootState state, int i) { }
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

    jambulController.startSearch(mac, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel deviceModel) {
        Log.d(TAG, "Jambul workState=" + state);
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState sw) {
        Log.d(TAG, "Bluetooth switch=" + sw);
      }
    });

    isJambulSessionRunning = true;
    selectedDevice = model;

    JSObject evt = new JSObject();
    evt.put("status", "session_started");
    evt.put("deviceName", model.getDeviceName());
    evt.put("deviceAddress", model.getDeviceMac());
    notifyListeners("deviceConnectionChanged", evt);

    notifyDeviceInfo(evt);
  }

  @PluginMethod
  public void startScan(PluginCall call) {
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

        // Same behavior as Lefu: auto start your session/search for that device.
        startJambulSession(model);

        JSObject result = new JSObject();
        result.put("connected", true);
        result.put("deviceName", model.getDeviceName());
        result.put("deviceAddress", model.getDeviceMac());
        notifyListeners("lefuDeviceInfo", result);

        if (resolved.compareAndSet(false, true)) {
          call.resolve(result);
        }
      }
    }, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState state, PPDeviceModel deviceModel) {
        Log.d(TAG, "Scan state: " + state);
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState sw) {
        if (sw == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is OFF");
        }
      }
    });

    // match Lefu style: return immediate "scanning started" if nothing yet
    JSObject result = new JSObject();
    result.put("connected", false);
    result.put("message", "Scanning started. Waiting for devices...");
    notifyListeners("lefuDeviceInfo", result);
    if (resolved.compareAndSet(false, true)) {
      call.resolve(result);
    }
  }

  @PluginMethod
  public void stopScan(PluginCall call) {
    if (ppScale != null) {
      try { ppScale.stopSearch(); } catch (Exception ignore) {}
      Log.d(TAG, "Scan stopped");
    }
    call.resolve();
  }

  @PluginMethod
  public void stopJambul(PluginCall call) {
    if (jambulController != null) {
      try { jambulController.registDataChangeListener(null); } catch (Exception ignore) {}
      try { jambulController.stopSeach(); } catch (Exception ignore) {}
    }

    isJambulSessionRunning = false;
    selectedDevice = null;

    JSObject evt = new JSObject();
    evt.put("status", "session_stopped");
    notifyListeners("deviceConnectionChanged", evt);

    call.resolve();
  }

  @PluginMethod
  public void getDevices(PluginCall call) {
    if (foundDevices.isEmpty()) {
      call.reject("No devices found.");
      return;
    }

    JSObject result = new JSObject();
    List<JSObject> list = new ArrayList<>();

    for (PPDeviceModel d : foundDevices) {
      JSObject o = new JSObject();
      o.put("deviceName", d.getDeviceName());
      o.put("deviceAddress", d.getDeviceMac());
      list.add(o);
    }

    result.put("devices", list);
    call.resolve(result);
  }

  @PluginMethod
  public void checkDeviceConnection(PluginCall call) {
    JSObject result = new JSObject();
    result.put("isConnected", isJambulSessionRunning);

    if (isJambulSessionRunning && selectedDevice != null) {
      result.put("deviceName", selectedDevice.getDeviceName());
      result.put("deviceAddress", selectedDevice.getDeviceMac());
    }

    call.resolve(result);
  }

  @PluginMethod
  public void syncUserInfo(PluginCall call) {
    if (!call.hasOption("age") || !call.hasOption("height") || !call.hasOption("sex")) {
      call.reject("Missing required user information: age, height, or sex.");
      return;
    }

    age = call.getInt("age");
    height = call.getDouble("height");
    gender = call.getString("sex").toLowerCase();

    PPUserGender sex = "male".equals(gender)
      ? PPUserGender.PPUserGenderMale
      : PPUserGender.PPUserGenderFemale;

    userModel = new PPUserModel.Builder()
      .setSex(sex)
      .setHeight(height.intValue())
      .setAge(age)
      .build();

    Log.d(TAG, "User info synced: age=" + age + " height=" + height + " sex=" + gender);
    call.resolve();
  }

  /**
   * IMPORTANT:
   * - No setKeepAlive
   * - No pendingMeasurementCall
   * - Same behavior as your LefuPlugin: resolve(call) when lock data arrives
   */
  @PluginMethod
  public void startMeasurement(PluginCall call) {
    Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(() ->
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      );
    }

    if (!isJambulSessionRunning || selectedDevice == null) {
      call.reject("No Jambul session running. Start scan first.");
      return;
    }

    if (userModel == null) {
      call.reject("User info missing. Call syncUserInfo first.");
      return;
    }

    if (jambulController == null) {
      call.reject("Jambul controller not initialized.");
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
        // if you want to mirror Lefu events, you can emit it here
        JSObject evt = new JSObject();
        evt.put("event", "bodyFatMeasurementStarted");
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
            Log.d("Clla", "Calculate Type"+ct);
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

          JSObject fieldsData = new JSObject();
          Field[] fields = fatModel.getClass().getDeclaredFields();
          for (Field field : fields) {
            try {
              field.setAccessible(true);
              Object v = field.get(fatModel);

              if (v instanceof Number) {
                fieldsData.put(field.getName(), (Number) v);
              } else if (v instanceof Boolean) {
                fieldsData.put(field.getName(), (Boolean) v);
              } else {
                fieldsData.put(field.getName(), v != null ? String.valueOf(v) : "N/A");
              }
            } catch (IllegalAccessException ignore) {}
          }

          JSObject result = new JSObject();
          result.put("fields", fieldsData);

          // Optional debug
          PPBodyDetailModel detail = new PPBodyDetailModel(fatModel);
          Log.d(TAG, "Body detail: " + detail.toString());

          resolved.set(true);

          // restore default listener for the running session
          ensureJambulListener();
          jambulController.registDataChangeListener(jambulDataChangeListener);

          if (activity != null) {
            activity.runOnUiThread(() ->
              activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            );
          }

          call.resolve(result);

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

          call.reject("Calculation failed: " + e.getMessage());
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

        call.reject("Jambul data fail");
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

    // Swap listener for this measurement only
    jambulController.registDataChangeListener(oneShotListener);

    JSObject evt = new JSObject();
    evt.put("event", "measurementStarted");
    notifyListeners("measurementUpdate", evt);

    // Do NOT resolve here. Resolve from lock.
    // This matches your Lefu behavior: return only when fields are ready.
  }

  @PluginMethod
  public void removeConnectedDevice(PluginCall call) {
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

      JSObject evt = new JSObject();
      evt.put("status", "session_stopped");
      notifyListeners("deviceConnectionChanged", evt);

      JSObject res = new JSObject();
      res.put("ok", true);
      res.put("message", "Session stopped");
      call.resolve(res);
    } catch (Exception e) {
      Log.e(TAG, "removeConnectedDevice failed (ignored)", e);
      JSObject res = new JSObject();
      res.put("ok", true);
      res.put("warning", String.valueOf(e.getMessage()));
      call.resolve(res);
    }
  }
}
