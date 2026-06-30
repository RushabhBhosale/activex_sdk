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
import com.peng.ppscale.business.ble.listener.PPBleSendResultCallBack;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.device.PeripheralBorre.PPBlutoothPeripheralBorreController;
import com.peng.ppscale.device.PeripheralTorre.PPBlutoothPeripheralTorreController;
import com.peng.ppscale.search.PPSearchManager;
import com.peng.ppscale.vo.PPScaleSendState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class LefuPlugin {
  private static final String TAG = "LefuPlugin";

  private final Context context;
  private LefuEventListener eventListener;
  private PPSearchManager ppScale;
  private List<PPDeviceModel> foundDevices = new ArrayList<>();
  private PPBlutoothPeripheralTorreController controller;
  private PPBlutoothPeripheralBorreController borreController;
  private boolean isDeviceConnected = false;
  private PPDeviceModel connectedDevice = null;
  private String calculateType = "";
  private Integer age = 0;
  private String gender = "";
  private Double height = 0.0;
  private PPUserModel userModel;

  public LefuPlugin(Context context) {
    this.context = context.getApplicationContext();
  }

  public void setEventListener(LefuEventListener eventListener) {
    this.eventListener = eventListener;
  }

  private void notifyListeners(String eventName, ResultData data) {
    if (eventListener != null) eventListener.onEvent(eventName, data);
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

    if (appKey == null || appSecret == null) {
      Log.e(TAG, "AppKey or AppSecret not provided");
      reject(callback, "AppKey or AppSecret not provided");
      return;
    }

    Log.d(TAG, "Initializing SDK with appKey in Lefu: " + appKey);
    PPBluetoothKit.INSTANCE.initSdk(context, appKey, appSecret, "lefu.config");
    PPCalculateKit.INSTANCE.initSdk(context);
    Log.d(TAG, "SDK initialized successfully");
    resolve(callback);
  }

  public void startScan(ResultCallback callback) {
    if (ppScale == null) {
      ppScale = PPSearchManager.getInstance();
    }

    foundDevices.clear();
    Set<String> deviceMacSet = new HashSet<>();
    AtomicBoolean isDeviceConnected = new AtomicBoolean(false);

    ppScale.startSearchDeviceList(300000, new PPSearchDeviceInfoInterface() {
      @Override
      public void onSearchDevice(PPDeviceModel ppDeviceModel, String data) {
        if (!deviceMacSet.contains(ppDeviceModel.getDeviceMac())) {
          Log.d(TAG, "Found unique device: " + ppDeviceModel.toString());
          calculateType = ppDeviceModel.getDeviceCalcuteType().toString();
          foundDevices.add(ppDeviceModel);
          deviceMacSet.add(ppDeviceModel.getDeviceMac());

          isDeviceConnected.set(true);
          Log.d(TAG, "Auto-connecting to device " + ppDeviceModel.getDeviceName() + "...");
          connectToDevice(ppDeviceModel);

          ResultData result = new ResultData();
          result.putValue("connected", true);
          result.putValue("deviceName", ppDeviceModel.getDeviceName());
          result.putValue("deviceAddress", ppDeviceModel.getDeviceMac());
          notifyListeners("lefuDeviceInfo", result);
          resolve(callback, result);
        }
      }
    }, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateSearching:
            Log.d(TAG, "Scanning for devices...");
            break;
          case PPBleWorkSearchTimeOut:
            Log.d(TAG, "Scan timeout");
            break;
          case PPBleWorkSearchFail:
            Log.e(TAG, "Scan failed");
            break;
          case PPBleStateSearchCanceled:
            Log.d(TAG, "Scan canceled");
            break;
          default:
            Log.d(TAG, "Other Bluetooth state: " + ppBleWorkState);
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState ppBleSwitchState) {
        if (ppBleSwitchState == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is off.");
        }
      }
    });

    if (!isDeviceConnected.get()) {
      ResultData result = new ResultData();
      result.putValue("connected", false);
      result.putValue("message", "Scanning started. Waiting for devices...");
      notifyListeners("lefuDeviceInfo", result);
      resolve(callback, result);
    }
  }

  private void connectToDevice(PPDeviceModel ppDeviceModel) {
    if (controller == null) {
      controller = new PPBlutoothPeripheralTorreController();
    }

    controller.startConnect(ppDeviceModel, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateConnected:
            Log.d(TAG, "Device connected successfully.");
            isDeviceConnected = true;
            connectedDevice = deviceModel;
            notifyDeviceConnected(ppDeviceModel);
            break;
          case PPBleWorkStateConnectFailed:
            Log.e(TAG, "Failed to connect to device.");
            isDeviceConnected = false;
            connectedDevice = null;
            break;
          case PPBleWorkStateDisconnected:
            Log.d(TAG, "device disconnected.");
            ResultData data = new ResultData();
            data.putValue("message", "Device disconnected");
            Log.d(TAG, "Notifying listeners about device disconnection");
            notifyListeners("deviceDisconnected", data);
            Log.d(TAG, "Listeners notified successfully");
            isDeviceConnected = false;
            connectedDevice = null;
            break;
          default:
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState ppBleSwitchState) {
        if (ppBleSwitchState == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is off.");
        }
      }
    });
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

  private void notifyDeviceConnected(PPDeviceModel ppDeviceModel) {
    ResultData result = new ResultData();
    result.putValue("deviceName", ppDeviceModel.getDeviceName());
    result.putValue("deviceAddress", ppDeviceModel.getDeviceMac());
    notifyDeviceInfo(result);
  }

  public void getDevices(ResultCallback callback) {
    if (foundDevices.isEmpty()) {
      Log.d(TAG, "No devices found.");
      reject(callback, "No devices found.");
      return;
    }

    ResultData result = new ResultData();
    List<ResultData> deviceList = new ArrayList<>();
    for (PPDeviceModel device : foundDevices) {
      ResultData deviceInfo = new ResultData();
      deviceInfo.putValue("deviceName", device.getDeviceName());
      deviceInfo.putValue("deviceAddress", device.getDeviceMac());
      deviceList.add(deviceInfo);
    }

    result.putValue("devices", deviceList);
    resolve(callback, result);
  }

  public void stopScan(ResultCallback callback) {
    if (ppScale != null) {
      ppScale.stopSearch();
      Log.d(TAG, "Scanning stopped.");
      resolve(callback);
    } else {
      Log.e(TAG, "Scan manager is not initialized.");
      reject(callback, "Scan manager is not initialized.");
    }
  }

  public void connectToDevice(String deviceAddress, String deviceName, ResultCallback callback) {
    if (deviceAddress == null || deviceAddress.isEmpty()) {
      reject(callback, "Device address is missing.");
      return;
    }
    if (deviceName == null || deviceName.isEmpty()) {
      reject(callback, "deviceName cannot be null or empty");
      return;
    }

    PPDeviceModel deviceModelObject = new PPDeviceModel(deviceAddress, deviceName);
    if (controller == null) {
      controller = new PPBlutoothPeripheralTorreController();
    }

    String finalDeviceName = deviceName;
    String finalDeviceAddress = deviceAddress;
    controller.startConnect(deviceModelObject, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateConnected:
            Log.d(TAG, "Device connected.");
            ResultData connected = new ResultData();
            connected.putValue("deviceName", finalDeviceName);
            connected.putValue("deviceAddress", finalDeviceAddress);
            resolve(callback, connected);
            break;
          case PPBleWorkStateConnectFailed:
            Log.e(TAG, "Connection failed.");
            reject(callback, "Connection failed");
            break;
          case PPBleWorkStateDisconnected:
            Log.d(TAG, "Device disconnected.");
            ResultData measurementStop = new ResultData();
            measurementStop.putValue("event", "measurementStop");
            notifyListeners("measurementUpdate", measurementStop);
            break;
          default:
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState ppBleSwitchState) {
        if (ppBleSwitchState == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is off.");
        }
      }
    });
  }

  public void syncUserInfo(Integer age, Double height, String sexValue, ResultCallback callback) {
    if (age == null || height == null || sexValue == null) {
      reject(callback, "Missing required user information: age, height, or sex.");
      return;
    }

    this.age = age;
    this.height = height;
    gender = sexValue.toLowerCase();
    PPUserGender sex = "male".equals(gender) ? PPUserGender.PPUserGenderMale : PPUserGender.PPUserGenderFemale;

    userModel = new PPUserModel.Builder()
      .setSex(sex)
      .setHeight(this.height.intValue())
      .setAge(this.age)
      .build();

    Log.d("SyncUserInfo", "User info synced: Age=" + this.age + ", Height=" + this.height + ", Sex=" + gender);
    resolve(callback);
  }

  public void startMeasurement(Activity activity, ResultCallback callback) {
    if (activity == null) {
      reject(callback, "Activity is required to start measurement.");
      return;
    }

    activity.runOnUiThread(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    if (controller != null) {
      controller.getTorreDeviceManager().registDataChangeListener(new PPDataChangeListener() {
        @Override
        public void monitorFootLenMeasure(PPScaleFootState ppScaleFootState, int i) {}

        @Override
        public void onImpedanceFatting() {
          Log.d(TAG, "Impedance fatting detected.");
          ResultData bodyFatMeasurementStarted = new ResultData();
          bodyFatMeasurementStarted.putValue("event", "bodyFatMeasurementStarted");
          notifyListeners("measurementUpdate", bodyFatMeasurementStarted);
        }

        @Override
        public void onDeviceShutdown() {
          Log.d(TAG, "Device has been shutdown.");
        }

        @Override
        public void monitorScaleState(@Nullable PPScaleState ppScaleState) {
          if (ppScaleState != null) {
            Log.d(TAG, "Scale state monitored: " + ppScaleState.toString());
            ResultData stateUpdate = new ResultData();
            stateUpdate.putValue("event", "scaleState");
            stateUpdate.putValue("state", ppScaleState.toString());
          } else {
            Log.d(TAG, "No scale state data received.");
          }
        }

        @Override
        public void monitorProcessData(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
          if (ppBodyBaseModel != null) {
            ResultData measurementStarted = new ResultData();
            measurementStarted.putValue("event", "measurementStarted");
            measurementStarted.putValue("weight", ppBodyBaseModel.getWeight());
            notifyListeners("measurementUpdate", measurementStarted);
            Log.d(TAG, "Basic Body Data: Weight: " + ppBodyBaseModel.getWeight()
              + ", Impedance: " + ppBodyBaseModel.getImpedance()
              + ", Heart Rate: " + ppBodyBaseModel.getHeartRate());
          }

          if (ppDeviceModel != null) {
            Log.d(TAG, "Device Model Data: DeviceName: " + ppDeviceModel.getDeviceName()
              + ", Power: " + ppDeviceModel.getDevicePower()
              + ", RSSI: " + ppDeviceModel.getRssi());
          }
        }

        @Override
        public void monitorOverWeight() {
          Log.d(TAG, "Overweight monitored.");
        }

        @Override
        public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo ppBodyFatInScaleVo) {
          if (ppBodyFatInScaleVo != null) {
            Log.d(TAG, "Lock data calculated in scale: " + ppBodyFatInScaleVo.toString());
          } else {
            Log.d(TAG, "No lock data by calculation in scale.");
          }
        }

        @Override
        public void monitorLockData(PPBodyBaseModel bodyBaseModel, PPDeviceModel deviceModel) {
          if (bodyBaseModel != null && deviceModel != null) {
            if (bodyBaseModel.isHeartRating()) {
              Log.d(TAG, "Heart rate is measuring. Locking weight data.");

              ResultData heartRateUpdate = new ResultData();
              heartRateUpdate.putValue("event", "heartRateMeasuring");
              notifyListeners("measurementUpdate", heartRateUpdate);

              String weightStr = PPUtil.getWeightValueD(
                bodyBaseModel.getUnit(),
                bodyBaseModel.getPpWeightKg(),
                deviceModel.getDeviceAccuracyType().getType(),
                true
              );
              Log.d(TAG, "Lock data: " + weightStr + " " + PPUtil.getWeightUnit(bodyBaseModel.getUnit()));

              try {
                double weight = Double.parseDouble(weightStr);
                bodyBaseModel.setWeight((int) (weight));
                Log.d(TAG, "Weight after multiplying by 100: " + bodyBaseModel.getWeight());
              } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse weight: " + weightStr, e);
              }
            } else {
              Log.d(TAG, "Measurement complete. Starting body fat calculation.");
              activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));

              ResultData measurementComplete = new ResultData();
              measurementComplete.putValue("event", "measurementComplete");
              notifyListeners("measurementUpdate", measurementComplete);

              String weightStr = PPUtil.getWeightValueD(
                bodyBaseModel.getUnit(),
                bodyBaseModel.getPpWeightKg(),
                deviceModel.getDeviceAccuracyType().getType(),
                true
              );
              Log.d(TAG, "Lock data: " + weightStr + " " + PPUtil.getWeightUnit(bodyBaseModel.getUnit()));

              try {
                double weight = Double.parseDouble(weightStr);
                bodyBaseModel.setWeight((int) weight);
                Log.d(TAG, "Weight after multiplying by 100: " + bodyBaseModel.getWeight());
              } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse weight: " + weightStr, e);
              }

              Log.d(TAG, "User info received: Age=" + age + ", Height=" + height + ", Sex=" + gender);
              String dynamicDeviceName = "";
              if (connectedDevice != null && connectedDevice.getDeviceName() != null) {
                dynamicDeviceName = connectedDevice.getDeviceName();
              } else if (deviceModel.getDeviceName() != null) {
                dynamicDeviceName = deviceModel.getDeviceName();
              } else {
                Log.d(TAG, "Using default device name: " + dynamicDeviceName);
              }
              deviceModel = new PPDeviceModel("", dynamicDeviceName);

              Log.d(TAG, "Calujjte rtyope" + calculateType);
              double weight = Double.parseDouble(weightStr);
              int weightInCents = (int) (weight * 100);

              double heartRate = Double.parseDouble(String.valueOf(bodyBaseModel.getHeartRate()));
              int heart = (int) (heartRate);
              deviceModel.setDeviceCalcuteType(PPScaleDefine.PPDeviceCalcuteType.valueOf(calculateType));
              Log.d(TAG, "Calulate type" + deviceModel.getDeviceCalcuteType());

              bodyBaseModel.setWeight(weightInCents);
              bodyBaseModel.setUserModel(userModel);
              bodyBaseModel.setSecret(SecretManager.getSecret(deviceModel.getDeviceCalcuteType().getType()));
              PPBodyFatModel fatModel = new PPBodyFatModel(bodyBaseModel, bodyBaseModel);
              ResultData result = new ResultData();

              Field[] fields = fatModel.getClass().getDeclaredFields();
              ResultData fieldsData = new ResultData();
              for (Field field : fields) {
                try {
                  field.setAccessible(true);
                  String fieldName = field.getName();
                  Object fieldValue = field.get(fatModel);
                  fieldsData.putValue(fieldName, fieldValue != null ? fieldValue.toString() : "N/A");
                  Log.d(TAG, "Field: " + fieldName + " | Value: " + (fieldValue != null ? fieldValue.toString() : "N/A"));
                } catch (IllegalAccessException e) {
                  Log.e(TAG, "Failed to access field: " + field.getName(), e);
                }
              }

              result.putValue("fields", fieldsData);
              Log.d(TAG, "fieldsData JSON: " + fieldsData);
              resolve(callback, result);

              PPBodyDetailModel ppDetailModel = new PPBodyDetailModel(fatModel);
              Log.d(TAG, "Body fat detail model:" + ppDetailModel.toString());
            }
          }
        }

        @Override
        public void monitorDataFail(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
          Log.d(TAG, "Data failed to process.");

          ResultData errorUpdate = new ResultData();
          errorUpdate.putValue("event", "dataFailure");
          errorUpdate.putValue("message", "Data failed to process.");
          notifyListeners("measurementUpdate", errorUpdate);

          if (ppBodyBaseModel != null && ppDeviceModel != null) {
            Log.d(TAG, "Failed data - BodyBaseModel: " + ppBodyBaseModel.toString() + ", DeviceModel: " + ppDeviceModel.toString());
          }
        }
      });

      controller.getTorreDeviceManager().startMeasure(new PPBleSendResultCallBack() {
        @Override
        public void onResult(PPScaleSendState sendState) {
          Log.d(TAG, "Measurement result: " + sendState.toString());
        }
      });
    } else {
      Log.e(TAG, "Controller not initialized.");
      reject(callback, "Controller not initialized.");
    }
  }
}
