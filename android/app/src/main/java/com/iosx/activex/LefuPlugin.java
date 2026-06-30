package com.iosx.activex;

import static com.iosx.activex.ICPlugin.notifyDeviceInfo;

import android.app.Activity;
import android.content.Context;
import android.util.Log; // Add this import for logging
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lefu.ppbase.PPBodyBaseModel;
import com.lefu.ppbase.PPScaleDefine;
import com.lefu.ppbase.util.PPUtil;
import com.lefu.ppbase.vo.PPBodyFatInScaleVo;
import com.lefu.ppbase.vo.PPScaleFootState;
import com.lefu.ppbase.vo.PPScaleState;
import com.lefu.ppbase.vo.PPUnitType;
import com.lefu.ppbase.vo.PPUserGender;
import com.lefu.ppbase.vo.PPUserModel;
import com.lefu.ppcalculate.PPBodyFatModel;
import com.lefu.ppcalculate.vo.PPBodyDetailModel;
import com.peng.ppscale.PPBluetoothKit;
import com.lefu.ppcalculate.PPCalculateKit;
import com.peng.ppscale.business.ble.listener.PPBleSendResultCallBack;
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPDeviceInfoInterface;
import com.peng.ppscale.business.ble.listener.PPUserInfoInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.device.PeripheralBorre.PPBlutoothPeripheralBorreController;
import com.peng.ppscale.device.PeripheralTorre.PPBlutoothPeripheralTorreController;
import com.peng.ppscale.search.PPSearchManager;
import com.lefu.ppbase.PPDeviceModel;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.vo.PPScaleSendState;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "LefuPlugin")
public class LefuPlugin extends Plugin {

    private static final String TAG = "LefuPlugin"; // Define a tag for the log
    private PPSearchManager ppScale; // For scanning devices
    private List<PPDeviceModel> foundDevices = new ArrayList<>();
    private PPBlutoothPeripheralTorreController controller;
    private PPBlutoothPeripheralBorreController borreController;
    private boolean isDeviceConnected = false;
    private PPDeviceModel connectedDevice = null;
    private  String calculateType = "";
    private Integer age = 0;
    private String gender = "";
    private Double height = (double) 0;
    private PPUserModel userModel;


  @PluginMethod
    public void initializeSDK(PluginCall call) {
        String appKey = "lefu0c091646522ebd05";
        String appSecret = "7MIs7ILShqp84endnTlTJMFG59iVB0BuejBWlhgq7+E=";

        if (appKey == null || appSecret == null) {
            Log.e(TAG, "AppKey or AppSecret not provided");
            call.reject("AppKey or AppSecret not provided");
            return;
        }

        Log.d(TAG, "Initializing SDK with appKey in Lefu: " + appKey);

        // Initialize the SDK
        Context context = getContext(); // Get the context from the Plugin
        PPBluetoothKit.INSTANCE.initSdk(context, appKey, appSecret, "lefu.config");
        PPCalculateKit.INSTANCE.initSdk(context);

        Log.d(TAG, "SDK initialized successfully");

        call.resolve();
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        if (ppScale == null) {
          ppScale = PPSearchManager.getInstance();
        }

        // Clear previous devices before starting a new scan
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

                JSObject result = new JSObject();
                result.put("connected", true);
                result.put("deviceName", ppDeviceModel.getDeviceName());
                result.put("deviceAddress", ppDeviceModel.getDeviceMac());
                notifyListeners("lefuDeviceInfo", result);
                call.resolve(result);
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

        // If CF610_G is not found, resolve the call after initiating the scan
        if (!isDeviceConnected.get()) {
            JSObject result = new JSObject();
            result.put("connected", false);
            result.put("message", "Scanning started. Waiting for devices...");
            notifyListeners("lefuDeviceInfo", result);
            call.resolve(result);
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
            isDeviceConnected = true; // Update connection status
            connectedDevice = deviceModel; // Save connected device
            notifyDeviceConnected(ppDeviceModel);
            break;
          case PPBleWorkStateConnectFailed:
            Log.e(TAG, "Failed to connect to device.");
            isDeviceConnected = false;
            connectedDevice = null;
            break;
          case PPBleWorkStateDisconnected:
            Log.d(TAG, "device disconnected."); // Check if this is printed
            JSObject data = new JSObject();
            data.put("message", "Device disconnected");
            Log.d(TAG, "Notifying listeners about device disconnection"); // Add log here
            notifyListeners("deviceDisconnected", data);
            Log.d(TAG, "Listeners notified successfully"); // Add log here
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

  @PluginMethod
  public void checkDeviceConnection(PluginCall call) {
    JSObject result = new JSObject();
    result.put("isConnected", isDeviceConnected);

    if (isDeviceConnected && connectedDevice != null) {
      result.put("deviceName", connectedDevice.getDeviceName());
      result.put("deviceAddress", connectedDevice.getDeviceMac());
    }

    call.resolve(result);
  }


    private void notifyDeviceConnected(PPDeviceModel ppDeviceModel) {
        // Notify the JavaScript layer about the successful connection
        JSObject result = new JSObject();
        result.put("deviceName", ppDeviceModel.getDeviceName());
        result.put("deviceAddress", ppDeviceModel.getDeviceMac());
        notifyDeviceInfo(result);
    }


    @PluginMethod
    public void getDevices(PluginCall call) {
        if (foundDevices.isEmpty()) {
            Log.d(TAG, "No devices found.");
            call.reject("No devices found.");
        } else {
            JSObject result = new JSObject();
            List<JSObject> deviceList = new ArrayList<>();

            for (PPDeviceModel device : foundDevices) {
                JSObject deviceInfo = new JSObject();
                deviceInfo.put("deviceName", device.getDeviceName());
                deviceInfo.put("deviceAddress", device.getDeviceMac());
                deviceList.add(deviceInfo);
            }

            result.put("devices", deviceList);
            call.resolve(result); // Return the list of devices as a JSObject
        }
    }


    @PluginMethod
    public void stopScan(PluginCall call) {

        if (ppScale != null) {
            ppScale.stopSearch();
            Log.d(TAG, "Scanning stopped.");
        } else {
            Log.e(TAG, "Scan manager is not initialized.");
            call.reject("Scan manager is not initialized.");
        }
    }

    @PluginMethod
    public void connectToDevice(PluginCall call) {
        JSONObject data = call.getData();
        Log.d(TAG, "Received data: " + data.toString());

        String deviceAddress = null;
        String deviceName = null;

        // Extract deviceModel object
        JSONObject deviceModel = null;
        try {
            deviceModel = data.getJSONObject("deviceModel");
        } catch (JSONException e) {
            Log.e(TAG, "Error extracting deviceModel", e);
            call.reject("Error extracting deviceModel", e);
            return;
        }

        // Extract deviceAddress from deviceModel
        try {
            if (deviceModel.has("deviceMac")) {
                deviceAddress = deviceModel.getString("deviceMac");
            } else if (deviceModel.has("deviceAddress")) {
                deviceAddress = deviceModel.getString("deviceAddress");
            } else {
                throw new IllegalArgumentException("Device address is missing.");
            }

            Log.d(TAG, "Device Address: " + deviceAddress);
        } catch (JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Error extracting device address", e);
            call.reject("Error extracting device address", e);
            return;
        }

        // Extract deviceName from deviceModel
        try {
            deviceName = deviceModel.getString("deviceName");
            if (deviceName == null || deviceName.isEmpty()) {
                throw new IllegalArgumentException("deviceName cannot be null or empty");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error extracting deviceName", e);
            call.reject("Error extracting deviceName", e);
            return;
        }

        // Proceed with device connection
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
                        call.resolve(new JSObject().put("deviceName", finalDeviceName).put("deviceAddress", finalDeviceAddress));
                        break;
                    case PPBleWorkStateConnectFailed:
                        Log.e(TAG, "Connection failed.");
                        call.reject("Connection failed");
                        break;
                    case PPBleWorkStateDisconnected:
                        Log.d(TAG, "Device disconnected.");
                        JSObject measurementStop = new JSObject();
                        measurementStop.put("event", "measurementStop");
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

   @PluginMethod
    public void syncUserInfo(PluginCall call) {

        if (!call.hasOption("age") || !call.hasOption("height") || !call.hasOption("sex")) {
            call.reject("Missing required user information: age, height, or sex.");
            return;
        }

        age = call.getInt("age");
        height = call.getDouble("height");
        gender = call.getString("sex").toLowerCase();
        PPUserGender sex = "male".equals(gender) ? PPUserGender.PPUserGenderMale : PPUserGender.PPUserGenderFemale;

        userModel = new PPUserModel.Builder()
                .setSex(sex)
                .setHeight(height.intValue())
                .setAge(age)
                .build();

        Log.d("SyncUserInfo", "User info synced: Age=" + age + ", Height=" + height + ", Sex=" + gender);

        call.resolve();
    }



  @PluginMethod
    public void startMeasurement(PluginCall call) {
    Activity activity = getActivity();

    activity.runOnUiThread(() -> {
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    });
    if (controller != null) {
            // Register the data change listener separately to monitor scale data changes
            controller.getTorreDeviceManager().registDataChangeListener(new PPDataChangeListener() {

              @Override
              public void monitorFootLenMeasure(PPScaleFootState ppScaleFootState, int i) {

              }

              @Override
                public void onImpedanceFatting() {
                    Log.d(TAG, "Impedance fatting detected.");
                    JSObject bodyFatMeasurementStarted = new JSObject();
                    bodyFatMeasurementStarted.put("event", "bodyFatMeasurementStarted");
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
                         JSObject stateUpdate = new JSObject();
                        stateUpdate.put("event", "scaleState");
                        stateUpdate.put("state", ppScaleState.toString());
                    } else {
                        Log.d(TAG, "No scale state data received.");
                    }
                }

                @Override
                public void monitorProcessData(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
                    if (ppBodyBaseModel != null) {
                        JSObject measurementStarted = new JSObject();
                        measurementStarted.put("event", "measurementStarted");
                        measurementStarted.put("weight", ppBodyBaseModel.getWeight());
                        notifyListeners("measurementUpdate", measurementStarted);
                        Log.d(TAG, "Basic Body Data: Weight: " + ppBodyBaseModel.getWeight() +
                        ", Impedance: " + ppBodyBaseModel.getImpedance() +
                        ", Heart Rate: " + ppBodyBaseModel.getHeartRate());
                    }

                    if (ppDeviceModel != null) {
                        Log.d(TAG, "Device Model Data: DeviceName: " + ppDeviceModel.getDeviceName() +
                        ", Power: " + ppDeviceModel.getDevicePower() +
                        ", RSSI: " + ppDeviceModel.getRssi());
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
                            // Heart rate is still being measured, print message and lock weight data
                            Log.d(TAG, "Heart rate is measuring. Locking weight data.");

                            JSObject heartRateUpdate = new JSObject();
                            heartRateUpdate.put("event", "heartRateMeasuring");
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
                            bodyBaseModel.setWeight((int) (weight)); // Multiply weight by 100 and cast to int
                            Log.d(TAG, "Weight after multiplying by 100: " + bodyBaseModel.getWeight());

                          } catch (NumberFormatException e) {
                            Log.e(TAG, "Failed to parse weight: " + weightStr, e);
                          }
                        } else {
                            // Measurement complete, proceed with body fat calculation
                            Log.d(TAG, "Measurement complete. Starting body fat calculation.");
                          activity.runOnUiThread(() -> {
                            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                          });


                          JSObject measurementComplete = new JSObject();
                            measurementComplete.put("event", "measurementComplete");
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
                                bodyBaseModel.setWeight((int) weight); // Multiply weight by 100 and cast to int
                                Log.d(TAG, "Weight after multiplying by 100: " + bodyBaseModel.getWeight());
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse weight: " + weightStr, e);
                            }

                            Log.d(TAG, "User info received: Age=" + age + ", Height=" + height + ", Sex=" + gender);
                            String dynamicDeviceName = ""; // Default fallback
                            if (connectedDevice != null && connectedDevice.getDeviceName() != null) {
                              dynamicDeviceName = connectedDevice.getDeviceName();
                            } else if (deviceModel.getDeviceName() != null) {
                              dynamicDeviceName = deviceModel.getDeviceName();
                            } else {
                              Log.d(TAG, "Using default device name: " + dynamicDeviceName);
                            }
                            deviceModel = new PPDeviceModel("", dynamicDeviceName);

                            Log.d(TAG, "Calujjte rtyope" + calculateType);
//                            PPScaleDefine.PPDeviceCalcuteType calcuteType = PPScaleDefine.PPDeviceCalcuteType.PPDeviceCalcuteTypeAlternate8_2; // 8-electrode algorithm


                            double weight = Double.parseDouble(weightStr);
                            int weightInCents = (int) (weight * 100);

                            double heartRate = Double.parseDouble(String.valueOf(bodyBaseModel.getHeartRate()));
                            int heart = (int) (heartRate);
                            // Set calculation type
                            deviceModel.setDeviceCalcuteType(PPScaleDefine.PPDeviceCalcuteType.valueOf(calculateType));
                            Log.d(TAG, "Calulate type" + deviceModel.getDeviceCalcuteType());

                            bodyBaseModel.setWeight(weightInCents);
                            bodyBaseModel.setUserModel(userModel);
                            bodyBaseModel.setSecret(SecretManager.getSecret(deviceModel.getDeviceCalcuteType().getType()));
                            // Perform fat calculation
                            PPBodyFatModel fatModel = new PPBodyFatModel(bodyBaseModel, bodyBaseModel);
                            JSObject result = new JSObject();

                        // Use reflection to map all fields and their values inside fatModel
                            Field[] fields = fatModel.getClass().getDeclaredFields();
                            JSObject fieldsData = new JSObject();
                            for (Field field : fields) {
                                try {
                                    field.setAccessible(true);
                                    String fieldName = field.getName();
                                    Object fieldValue = field.get(fatModel);
                                    fieldsData.put(fieldName, fieldValue != null ? fieldValue.toString() : "N/A");
                                     Log.d(TAG, "Field: " + fieldName + " | Value: " + (fieldValue != null ? fieldValue.toString() : "N/A"));
                                } catch (IllegalAccessException e) {
                                    Log.e(TAG, "Failed to access field: " + field.getName(), e);
                                }
                            }

                            // Add fields data to result
                            result.put("fields", fieldsData);
                          Log.d(TAG, "fieldsData JSON: " + fieldsData);
                            call.resolve(result); // Return the result to Ionic side

                            // Calculate body fat range (PPBodyDetailModel)
                            PPBodyDetailModel ppDetailModel = new PPBodyDetailModel(fatModel);
                            Log.d(TAG, "Body fat detail model:" + ppDetailModel.toString());
                        }
                    }
                }

                @Override
                public void monitorDataFail(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
                    Log.d(TAG, "Data failed to process.");

                JSObject errorUpdate = new JSObject();
                errorUpdate.put("event", "dataFailure");
                errorUpdate.put("message", "Data failed to process.");
                notifyListeners("measurementUpdate", errorUpdate);

                    if (ppBodyBaseModel != null && ppDeviceModel != null) {
                        Log.d(TAG, "Failed data - BodyBaseModel: " + ppBodyBaseModel.toString() + ", DeviceModel: " + ppDeviceModel.toString());
                    }
                }

            });

            // Start the measurement with a PPBleSendResultCallBack if necessary for Bluetooth communication
            controller.getTorreDeviceManager().startMeasure(new PPBleSendResultCallBack() {
                @Override
                public void onResult(PPScaleSendState sendState) {
                    Log.d(TAG, "Measurement result: " + sendState.toString());
                }
            });
        } else {
            Log.e(TAG, "Controller not initialized.");
            call.reject("Controller not initialized.");
        }
    }
}
