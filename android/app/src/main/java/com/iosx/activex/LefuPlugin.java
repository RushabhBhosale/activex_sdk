package com.iosx.activex;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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
import com.lefu.ppcalculate.vo.BodyFatErrorType;
import com.peng.ppscale.PPBluetoothKit;
import com.peng.ppscale.business.ble.listener.PPBleSendResultCallBack;
import com.peng.ppscale.business.ble.listener.PPBleStateInterface;
import com.peng.ppscale.business.ble.listener.PPDataChangeListener;
import com.peng.ppscale.business.ble.listener.PPSearchDeviceInfoInterface;
import com.peng.ppscale.business.ble.listener.PPUserInfoInterface;
import com.peng.ppscale.business.state.PPBleSwitchState;
import com.peng.ppscale.business.state.PPBleWorkState;
import com.peng.ppscale.device.PeripheralBorre.PPBlutoothPeripheralBorreController;
import com.peng.ppscale.device.PeripheralTorre.PPBlutoothPeripheralTorreController;
import com.peng.ppscale.search.DeviceFilterHelper;
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
  public static final String SUPPORTED_DEVICE_NAME = "CF650_BG";

  private final Context context;
  private LefuEventListener eventListener;
  private PPSearchManager ppScale;
  private List<PPDeviceModel> foundDevices = new ArrayList<>();
  private PPBlutoothPeripheralTorreController controller;
  private PPBlutoothPeripheralBorreController borreController;
  private boolean connectedWithBorre = false;
  private BluetoothLeScanner borreFallbackScanner;
  private ScanCallback borreFallbackCallback;
  private final Set<String> borreFallbackAddresses = new HashSet<>();
  private final Set<String> borreFallbackConnectionAttempts = new HashSet<>();
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

  private void logMeasurementValues(PPBodyBaseModel body, PPBodyFatModel calculated) {
    Log.d(TAG, "Measurement values: errorType=" + calculated.getErrorType()
      + ", weightKg=" + calculated.getPpWeightKg()
      + ", impedance=" + body.getImpedance()
      + ", impedance100=" + body.getPpImpedance100DeCode()
      + ", twoLegImpedance=" + body.getZTwoLegsDeCode()
      + ", bmi=" + calculated.getPpBMI()
      + ", bmr=" + calculated.getPpBMR()
      + ", bodyFat=" + calculated.getPpFat()
      + ", bodyFatKg=" + calculated.getPpBodyfatKg()
      + ", musclePercentage=" + calculated.getPpMusclePercentage()
      + ", muscleKg=" + calculated.getPpMuscleKg()
      + ", waterPercentage=" + calculated.getPpWaterPercentage()
      + ", visceralFat=" + calculated.getPpVisceralFat()
      + ", heartRate=" + calculated.getPpHeartRate());

    Log.d(TAG, "Raw segment impedance: 100kHz[leftArm=" + body.getZ100KhzLeftArmEnCode()
      + ", rightArm=" + body.getZ100KhzRightArmEnCode()
      + ", leftLeg=" + body.getZ100KhzLeftLegEnCode()
      + ", rightLeg=" + body.getZ100KhzRightLegEnCode()
      + ", trunk=" + body.getZ100KhzTrunkEnCode()
      + "], 20kHz[leftArm=" + body.getZ20KhzLeftArmEnCode()
      + ", rightArm=" + body.getZ20KhzRightArmEnCode()
      + ", leftLeg=" + body.getZ20KhzLeftLegEnCode()
      + ", rightLeg=" + body.getZ20KhzRightLegEnCode()
      + ", trunk=" + body.getZ20KhzTrunkEnCode() + "]");

    Log.d(TAG, "Segment impedance 100kHz: leftArm=" + body.getZ100KhzLeftArmDeCode()
      + ", rightArm=" + body.getZ100KhzRightArmDeCode()
      + ", leftLeg=" + body.getZ100KhzLeftLegDeCode()
      + ", rightLeg=" + body.getZ100KhzRightLegDeCode()
      + ", trunk=" + body.getZ100KhzTrunkDeCode());

    Log.d(TAG, "Segment impedance 20kHz: leftArm=" + body.getZ20KhzLeftArmDeCode()
      + ", rightArm=" + body.getZ20KhzRightArmDeCode()
      + ", leftLeg=" + body.getZ20KhzLeftLegDeCode()
      + ", rightLeg=" + body.getZ20KhzRightLegDeCode()
      + ", trunk=" + body.getZ20KhzTrunkDeCode());

    Log.d(TAG, "Segment body fat kg: leftArm=" + calculated.getPpBodyFatKgLeftArm()
      + ", rightArm=" + calculated.getPpBodyFatKgRightArm()
      + ", leftLeg=" + calculated.getPpBodyFatKgLeftLeg()
      + ", rightLeg=" + calculated.getPpBodyFatKgRightLeg()
      + ", trunk=" + calculated.getPpBodyFatKgTrunk());

    Log.d(TAG, "Segment muscle kg: leftArm=" + calculated.getPpMuscleKgLeftArm()
      + ", rightArm=" + calculated.getPpMuscleKgRightArm()
      + ", leftLeg=" + calculated.getPpMuscleKgLeftLeg()
      + ", rightLeg=" + calculated.getPpMuscleKgRightLeg()
      + ", trunk=" + calculated.getPpMuscleKgTrunk());
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

  private boolean isBorreDevice(PPDeviceModel device) {
    if (device == null) return false;
    if (device.getDevicePeripheralType() == PPScaleDefine.PPDevicePeripheralType.PeripheralBorre) {
      return true;
    }

    PPScaleDefine.PPDeviceProtocolType protocol = device.getDeviceProtocolType();
    return protocol == PPScaleDefine.PPDeviceProtocolType.PPDeviceProtocolTypeBorre
      || protocol == PPScaleDefine.PPDeviceProtocolType.PPDeviceProtocolTypeBorre_A
      || protocol == PPScaleDefine.PPDeviceProtocolType.PPDeviceProtocolTypeBorre_B
      || protocol == PPScaleDefine.PPDeviceProtocolType.PPDeviceProtocolTypeBorre_C;
  }

  private boolean isSupportedDevice(PPDeviceModel device) {
    return device != null && isSupportedDeviceName(device.getDeviceName());
  }

  private boolean isSupportedDeviceName(String name) {
    return SUPPORTED_DEVICE_NAME.equals(name);
  }

  private void ensureController(boolean useBorre) {
    if (useBorre) {
      if (borreController == null) borreController = new PPBlutoothPeripheralBorreController();
    } else if (controller == null) {
      controller = new PPBlutoothPeripheralTorreController();
    }
  }

  private boolean hasController() {
    return connectedWithBorre ? borreController != null : controller != null;
  }

  private void startControllerConnection(
    PPDeviceModel device,
    boolean useBorre,
    PPBleStateInterface stateListener
  ) {
    ensureController(useBorre);
    if (useBorre) {
      borreController.startConnect(device, stateListener);
    } else {
      controller.startConnect(device, stateListener);
    }
  }

  private void registerDataChangeListener(PPDataChangeListener listener) {
    if (connectedWithBorre) {
      borreController.getTorreDeviceManager().registDataChangeListener(listener);
    } else {
      controller.getTorreDeviceManager().registDataChangeListener(listener);
    }
  }

  private void startControllerKeepAlive() {
    if (connectedWithBorre && borreController != null) {
      borreController.getTorreDeviceManager().startKeepAlive();
    } else if (controller != null) {
      controller.getTorreDeviceManager().startKeepAlive();
    }
  }

  private void stopControllerKeepAlive(boolean useBorre) {
    if (useBorre && borreController != null) {
      borreController.getTorreDeviceManager().stopKeepAlive();
    } else if (!useBorre && controller != null) {
      controller.getTorreDeviceManager().stopKeepAlive();
    }
  }

  private void startControllerMeasurement(PPBleSendResultCallBack callback) {
    if (connectedWithBorre) {
      borreController.getTorreDeviceManager().startMeasure(callback);
    } else {
      controller.getTorreDeviceManager().startMeasure(callback);
    }
  }

  private void syncBorreUserForMeasurement(Runnable onSuccess, Runnable onFailure) {
    if (!connectedWithBorre || borreController == null) {
      onSuccess.run();
      return;
    }

    AtomicBoolean syncHandled = new AtomicBoolean(false);
    Log.d(TAG, "Synchronizing the active user profile with the Borre scale.");
    borreController.getTorreDeviceManager().syncUserInfo(userModel, new PPUserInfoInterface() {
      @Override public void getUserListSuccess(List<String> users) {}
      @Override public void getUserListByUserModelSuccess(List<PPUserModel> users) {}

      @Override
      public void syncUserInfoSuccess() {
        if (!syncHandled.compareAndSet(false, true)) return;
        Log.d(TAG, "Borre user profile synchronized; measurement is ready.");
        onSuccess.run();
      }

      @Override
      public void syncUserInfoFail() {
        if (!syncHandled.compareAndSet(false, true)) return;
        Log.e(TAG, "Borre user profile synchronization failed.");
        onFailure.run();
      }

      @Override public void syncUserSevenWeightInfoSuccess() {}
      @Override public void syncUserSevenWeightInfoFail() {}
      @Override public void deleteUserInfoSuccess(PPUserModel user) {}
      @Override public void deleteUserInfoFail(PPUserModel user) {}
      @Override public void confirmCurrentUserInfoSuccess() {}
      @Override public void confirmCurrentUserInfoFail() {}
    });
  }

  private boolean hasBorreImpedance(PPBodyBaseModel body) {
    return body.getImpedance() > 0
      || body.getPpImpedance100EnCode() > 0
      || body.getZTwoLegsDeCode() > 0
      || body.getPpImpedance100DeCode() > 0
      || body.getZ20KhzLeftArmEnCode() > 0
      || body.getZ20KhzRightArmEnCode() > 0
      || body.getZ20KhzLeftLegEnCode() > 0
      || body.getZ20KhzRightLegEnCode() > 0
      || body.getZ20KhzTrunkEnCode() > 0
      || body.getZ100KhzLeftArmEnCode() > 0
      || body.getZ100KhzRightArmEnCode() > 0
      || body.getZ100KhzLeftLegEnCode() > 0
      || body.getZ100KhzRightLegEnCode() > 0
      || body.getZ100KhzTrunkEnCode() > 0
      || body.getZ20KhzLeftArmDeCode() > 0
      || body.getZ20KhzRightArmDeCode() > 0
      || body.getZ20KhzLeftLegDeCode() > 0
      || body.getZ20KhzRightLegDeCode() > 0
      || body.getZ20KhzTrunkDeCode() > 0
      || body.getZ100KhzLeftArmDeCode() > 0
      || body.getZ100KhzRightArmDeCode() > 0
      || body.getZ100KhzLeftLegDeCode() > 0
      || body.getZ100KhzRightLegDeCode() > 0
      || body.getZ100KhzTrunkDeCode() > 0;
  }

  private void finishScanningAfterConnection() {
    stopBorreFallbackScan();
    if (ppScale != null) {
      try {
        ppScale.stopSearch();
      } catch (Exception error) {
        Log.w(TAG, "Could not stop the scale scan after connecting.", error);
      }
    }
  }

  private void startBorreFallbackScan() {
    stopBorreFallbackScan();
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
    if (adapter == null || !adapter.isEnabled()) return;

    try {
      borreFallbackScanner = adapter.getBluetoothLeScanner();
      if (borreFallbackScanner == null) return;
      borreFallbackAddresses.clear();
      borreFallbackConnectionAttempts.clear();
      borreFallbackCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
          if (isDeviceConnected || result == null || result.getDevice() == null) return;
          BluetoothDevice bluetoothDevice = result.getDevice();
          String address = bluetoothDevice.getAddress();
          if (address == null || !borreFallbackAddresses.add(address)) return;

          String name;
          try {
            name = bluetoothDevice.getName();
          } catch (SecurityException error) {
            return;
          }
          if (!isBorreFallbackCandidate(name) || !borreFallbackConnectionAttempts.add(address)) return;

          byte[] scanRecord = result.getScanRecord() == null
            ? new byte[0]
            : result.getScanRecord().getBytes();
          com.lefu.bluetooth.library.search.SearchResult vendorSearchResult =
            new com.lefu.bluetooth.library.search.SearchResult(
              bluetoothDevice,
              result.getRssi(),
              scanRecord
            );
          PPDeviceModel parsedDevice = DeviceFilterHelper.INSTANCE.getDeviceModel(vendorSearchResult);
          PPDeviceModel device = parsedDevice != null
            ? parsedDevice
            : new PPDeviceModel(address, name);
          configureSupportedBorreDevice(device);
          Log.d(TAG, "Borre device configuration: protocol=" + device.getDeviceProtocolType()
            + ", calculation=" + device.getDeviceCalcuteType()
            + ", accuracy=" + device.getDeviceAccuracyType());
          calculateType = device.getDeviceCalcuteType().toString();
          foundDevices.add(device);
          notifyScanState("borre_fallback_connecting", "Connecting to the discovered Lefu Borre scale.");
          connectToDevice(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
          notifyScanState("borre_fallback_failed", "Lefu Borre fallback scan failed with Android error " + errorCode + ".");
        }
      };
      borreFallbackScanner.startScan(null, new ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build(), borreFallbackCallback);
    } catch (SecurityException error) {
      notifyScanState("borre_fallback_failed", "Lefu Borre fallback scan is missing Bluetooth permission.");
    }
  }

  private void stopBorreFallbackScan() {
    if (borreFallbackScanner == null || borreFallbackCallback == null) return;
    try {
      borreFallbackScanner.stopScan(borreFallbackCallback);
    } catch (SecurityException ignored) {
      // Bluetooth permission can be revoked while the fallback scan is active.
    }
    borreFallbackCallback = null;
    borreFallbackScanner = null;
    borreFallbackAddresses.clear();
    borreFallbackConnectionAttempts.clear();
  }

  private boolean isBorreFallbackCandidate(String name) {
    return isSupportedDeviceName(name);
  }

  private void configureSupportedBorreDevice(PPDeviceModel device) {
    device.setDeviceType(PPScaleDefine.PPDeviceType.PPDeviceTypeCF);
    device.setDeviceProtocolType(PPScaleDefine.PPDeviceProtocolType.PPDeviceProtocolTypeBorre);
    device.setDeviceCalcuteType(PPScaleDefine.PPDeviceCalcuteType.PPDeviceCalcuteTypeAlternate8_2);
    device.setDeviceAccuracyType(PPScaleDefine.PPDeviceAccuracyType.PPDeviceAccuracyTypePoint005);
    device.setDevicePowerType(PPScaleDefine.PPDevicePowerType.PPDevicePowerTypeCharge);
    device.setDeviceConnectType(PPScaleDefine.PPDeviceConnectType.PPDeviceConnectTypeDirect);
    device.setDeviceUnitType("0,1,11");
  }

  private PPScaleDefine.PPDeviceCalcuteType resolveCalculateType(PPDeviceModel scaleDevice) {
    if (scaleDevice != null && scaleDevice.getDeviceCalcuteType() != null) {
      return scaleDevice.getDeviceCalcuteType();
    }
    if (connectedDevice != null && connectedDevice.getDeviceCalcuteType() != null) {
      return connectedDevice.getDeviceCalcuteType();
    }
    if (calculateType != null && !calculateType.isEmpty()) {
      try {
        return PPScaleDefine.PPDeviceCalcuteType.valueOf(calculateType);
      } catch (IllegalArgumentException ignored) {
        // Use the safe calculation fallback below.
      }
    }
    return PPScaleDefine.PPDeviceCalcuteType.PPDeviceCalcuteTypeAlternate;
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
    AtomicBoolean scanCallbackResolved = new AtomicBoolean(false);

    ppScale.startSearchDeviceList(300000, new PPSearchDeviceInfoInterface() {
      @Override
      public void onSearchDevice(PPDeviceModel ppDeviceModel, String data) {
        if (!isSupportedDevice(ppDeviceModel)) return;
        configureSupportedBorreDevice(ppDeviceModel);

        if (!deviceMacSet.contains(ppDeviceModel.getDeviceMac())) {
          Log.d(TAG, "Found unique device: " + ppDeviceModel.toString());
          calculateType = ppDeviceModel.getDeviceCalcuteType().toString();
          foundDevices.add(ppDeviceModel);
          deviceMacSet.add(ppDeviceModel.getDeviceMac());

          Log.d(TAG, "Auto-connecting to device " + ppDeviceModel.getDeviceName() + "...");
          connectToDevice(ppDeviceModel);

          ResultData result = new ResultData();
          result.putValue("connected", false);
          result.putValue("state", "discovered");
          result.putValue("message", "Scale discovered; attempting connection.");
          result.putValue("deviceName", ppDeviceModel.getDeviceName());
          result.putValue("deviceAddress", ppDeviceModel.getDeviceMac());
          result.putValue("peripheralType", ppDeviceModel.getDevicePeripheralType().toString());
          result.putValue("protocolType", ppDeviceModel.getDeviceProtocolType().toString());
          notifyListeners("lefuDeviceInfo", result);
          if (scanCallbackResolved.compareAndSet(false, true)) {
            resolve(callback, result);
          }
        }
      }
    }, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateSearching:
            Log.d(TAG, "Scanning for devices...");
            notifyScanState("searching", "Scanning for Lefu scales...");
            break;
          case PPBleWorkSearchTimeOut:
            Log.d(TAG, "Scan timeout");
            notifyScanState("timeout", "No scale was found before the scan timed out.");
            break;
          case PPBleWorkSearchFail:
            Log.e(TAG, "Scan failed");
            notifyScanState("failed", "The Bluetooth scan failed.");
            break;
          case PPBleStateSearchCanceled:
            Log.d(TAG, "Scan canceled");
            notifyScanState("cancelled", "The Bluetooth scan was cancelled.");
            break;
          default:
            Log.d(TAG, "Other Bluetooth state: " + ppBleWorkState);
            notifyScanState("state", "Bluetooth scan state: " + ppBleWorkState);
            break;
        }
      }

      @Override
      public void monitorBluetoothSwitchState(PPBleSwitchState ppBleSwitchState) {
        if (ppBleSwitchState == PPBleSwitchState.PPBleSwitchStateOff) {
          Log.e(TAG, "Bluetooth is off.");
          notifyScanState("failed", "Bluetooth is turned off.");
        }
      }
    });

    startBorreFallbackScan();

    ResultData result = new ResultData();
    result.putValue("connected", false);
    result.putValue("state", "searching");
    result.putValue("message", "Scanning started. Waiting for devices...");
    notifyListeners("lefuDeviceInfo", result);
    if (scanCallbackResolved.compareAndSet(false, true)) {
      resolve(callback, result);
    }
  }

  private void connectToDevice(PPDeviceModel ppDeviceModel) {
    if (!isSupportedDevice(ppDeviceModel)) {
      Log.w(TAG, "Ignoring unsupported scale; this SDK build only accepts " + SUPPORTED_DEVICE_NAME + ".");
      return;
    }
    configureSupportedBorreDevice(ppDeviceModel);

    final boolean connectionUsesBorre = isBorreDevice(ppDeviceModel);
    notifyConnectionState(
      "connecting",
      connectionUsesBorre
        ? "Connecting with the Lefu Borre controller..."
        : "Connecting with the Lefu Torre controller...",
      ppDeviceModel
    );

    startControllerConnection(ppDeviceModel, connectionUsesBorre, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateConnected:
            Log.d(TAG, "Device connected successfully.");
            isDeviceConnected = true;
            connectedWithBorre = connectionUsesBorre;
            connectedDevice = deviceModel != null ? deviceModel : ppDeviceModel;
            finishScanningAfterConnection();
            startControllerKeepAlive();
            notifyConnectionState("connected", "Scale connected successfully.", connectedDevice);
            notifyDeviceConnected(ppDeviceModel);
            break;
          case PPBleWorkStateConnectFailed:
            Log.e(TAG, "Failed to connect to device.");
            isDeviceConnected = false;
            connectedDevice = null;
            stopControllerKeepAlive(connectionUsesBorre);
            notifyConnectionState("failed", "Could not connect to the discovered scale.", deviceModel != null ? deviceModel : ppDeviceModel);
            break;
          case PPBleWorkStateDisconnected:
            Log.d(TAG, "device disconnected.");
            ResultData data = new ResultData();
            data.putValue("message", "Device disconnected");
            notifyConnectionState("disconnected", "The scale disconnected.", deviceModel != null ? deviceModel : ppDeviceModel);
            Log.d(TAG, "Notifying listeners about device disconnection");
            notifyListeners("deviceDisconnected", data);
            Log.d(TAG, "Listeners notified successfully");
            isDeviceConnected = false;
            connectedDevice = null;
            stopControllerKeepAlive(connectionUsesBorre);
            connectedWithBorre = false;
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
    stopBorreFallbackScan();
    if (ppScale != null) {
      ppScale.stopSearch();
      Log.d(TAG, "Scanning stopped.");
    }
    resolve(callback);
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
    if (!isSupportedDeviceName(deviceName)) {
      reject(callback, "Unsupported scale. This SDK build only supports " + SUPPORTED_DEVICE_NAME + ".");
      return;
    }

    PPDeviceModel discoveredDevice = null;
    for (PPDeviceModel device : foundDevices) {
      if (deviceAddress.equals(device.getDeviceMac())) {
        discoveredDevice = device;
        break;
      }
    }
    final PPDeviceModel deviceModelObject = discoveredDevice != null
      ? discoveredDevice
      : new PPDeviceModel(deviceAddress, deviceName);
    configureSupportedBorreDevice(deviceModelObject);
    final boolean connectionUsesBorre = isBorreDevice(deviceModelObject);
    String finalDeviceName = deviceName;
    String finalDeviceAddress = deviceAddress;
    notifyConnectionState("connecting", "Connecting to the selected scale...", deviceModelObject);
    startControllerConnection(deviceModelObject, connectionUsesBorre, new PPBleStateInterface() {
      @Override
      public void monitorBluetoothWorkState(PPBleWorkState ppBleWorkState, PPDeviceModel deviceModel) {
        switch (ppBleWorkState) {
          case PPBleWorkStateConnected:
            Log.d(TAG, "Device connected.");
            isDeviceConnected = true;
            connectedWithBorre = connectionUsesBorre;
            connectedDevice = deviceModel != null ? deviceModel : deviceModelObject;
            finishScanningAfterConnection();
            startControllerKeepAlive();
            ResultData connected = new ResultData();
            connected.putValue("deviceName", finalDeviceName);
            connected.putValue("deviceAddress", finalDeviceAddress);
            notifyConnectionState("connected", "Scale connected successfully.", connectedDevice);
            resolve(callback, connected);
            break;
          case PPBleWorkStateConnectFailed:
            Log.e(TAG, "Connection failed.");
            isDeviceConnected = false;
            connectedDevice = null;
            stopControllerKeepAlive(connectionUsesBorre);
            notifyConnectionState("failed", "Could not connect to the selected scale.", deviceModel != null ? deviceModel : deviceModelObject);
            reject(callback, "Connection failed");
            break;
          case PPBleWorkStateDisconnected:
            Log.d(TAG, "Device disconnected.");
            isDeviceConnected = false;
            connectedDevice = null;
            stopControllerKeepAlive(connectionUsesBorre);
            connectedWithBorre = false;
            notifyConnectionState("disconnected", "The scale disconnected.", deviceModel != null ? deviceModel : deviceModelObject);
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

    if (!isDeviceConnected) {
      reject(callback, "No connected scale. Scan and wait for the connection to complete first.");
      return;
    }

    if (userModel == null) {
      reject(callback, "User profile is missing. Sync age, height, and sex before starting a measurement.");
      return;
    }

    activity.runOnUiThread(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
    final AtomicBoolean measurementResolved = new AtomicBoolean(false);
    if (hasController()) {
      startControllerKeepAlive();
      registerDataChangeListener(new PPDataChangeListener() {
        @Override
        public void monitorFootLenMeasure(PPScaleFootState ppScaleFootState, int i) {}

        @Override
        public void onImpedanceFatting() {
          Log.d(TAG, "Impedance measurement started.");
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
            ResultData stateUpdate = new ResultData();
            stateUpdate.putValue("event", "scaleState");
            stateUpdate.putValue("state", ppScaleState.toString());
            notifyListeners("measurementUpdate", stateUpdate);
          }
        }

        @Override
        public void monitorProcessData(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
          if (ppBodyBaseModel != null) {
            ResultData measurementStarted = new ResultData();
            measurementStarted.putValue("event", "measurementStarted");
            notifyListeners("measurementUpdate", measurementStarted);
            Log.d(TAG, "Live measurement: weightKg=" + ppBodyBaseModel.getPpWeightKg()
              + ", impedance=" + ppBodyBaseModel.getImpedance()
              + ", impedance100=" + ppBodyBaseModel.getPpImpedance100DeCode()
              + ", heartRate=" + ppBodyBaseModel.getHeartRate());
          }
        }

        @Override
        public void monitorOverWeight() {
          Log.d(TAG, "Overweight monitored.");
          ResultData overWeight = new ResultData();
          overWeight.putValue("event", "overWeight");
          notifyListeners("measurementUpdate", overWeight);
        }

        @Override
        public void monitorLockDataByCalculateInScale(@Nullable PPBodyFatInScaleVo ppBodyFatInScaleVo) {
          // The final normalized values are logged from monitorLockData.
        }

        @Override
        public void monitorLockData(PPBodyBaseModel bodyBaseModel, PPDeviceModel deviceModel) {
          if (bodyBaseModel == null || deviceModel == null) {
            Log.e(TAG, "monitorLockData: cannot calculate because body or device model is null.");
            return;
          }

          if (bodyBaseModel.isHeartRating()) {
              ResultData heartRateUpdate = new ResultData();
              heartRateUpdate.putValue("event", "heartRateMeasuring");
              notifyListeners("measurementUpdate", heartRateUpdate);

              String weightStr = PPUtil.getWeightValueD(
                bodyBaseModel.getUnit(),
                bodyBaseModel.getPpWeightKg(),
                deviceModel.getDeviceAccuracyType().getType(),
                true
              );
              try {
                double weight = Double.parseDouble(weightStr);
                bodyBaseModel.setWeight((int) (weight));
              } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse weight: " + weightStr, e);
              }
          } else {
              activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));

              if (connectedWithBorre && !hasBorreImpedance(bodyBaseModel)) {
                String message = "The Borre scale completed without impedance. Stand barefoot on all electrodes and hold the handles until completion, then retry.";
                Log.e(TAG, "Borre measurement failed: weightKg=" + bodyBaseModel.getPpWeightKg()
                  + ", impedance=" + bodyBaseModel.getImpedance()
                  + ", impedance100=" + bodyBaseModel.getPpImpedance100DeCode()
                  + ", twoLegImpedance=" + bodyBaseModel.getZTwoLegsDeCode());

                ResultData impedanceFailure = new ResultData();
                impedanceFailure.putValue("event", "dataFailure");
                impedanceFailure.putValue("message", message);
                notifyListeners("measurementUpdate", impedanceFailure);

                if (measurementResolved.compareAndSet(false, true)) {
                  reject(callback, message);
                }
                return;
              }

              ResultData measurementComplete = new ResultData();
              measurementComplete.putValue("event", "measurementComplete");
              notifyListeners("measurementUpdate", measurementComplete);

              String weightStr = PPUtil.getWeightValueD(
                bodyBaseModel.getUnit(),
                bodyBaseModel.getPpWeightKg(),
                deviceModel.getDeviceAccuracyType().getType(),
                true
              );
              try {
                double weight = Double.parseDouble(weightStr);
                bodyBaseModel.setWeight((int) weight);
              } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse weight: " + weightStr, e);
              }

              PPDeviceModel calculationDevice = connectedDevice != null ? connectedDevice : deviceModel;
              PPScaleDefine.PPDeviceCalcuteType deviceCalculateType = resolveCalculateType(calculationDevice);

              double weight = Double.parseDouble(weightStr);
              int weightInCents = (int) (weight * 100);

              calculationDevice.setDeviceCalcuteType(deviceCalculateType);

              bodyBaseModel.setWeight(weightInCents);
              bodyBaseModel.setUserModel(userModel);
              bodyBaseModel.setDeviceModel(calculationDevice);
              bodyBaseModel.setSecret(SecretManager.getSecret(deviceCalculateType.getType()));
              PPBodyFatModel fatModel = new PPBodyFatModel(bodyBaseModel, bodyBaseModel);
              logMeasurementValues(bodyBaseModel, fatModel);

              if (fatModel.getErrorType() != BodyFatErrorType.PP_ERROR_TYPE_NONE) {
                String message = "Body-composition calculation failed: " + fatModel.getErrorType() + ". Retry with full electrode contact.";
                ResultData calculationFailure = new ResultData();
                calculationFailure.putValue("event", "dataFailure");
                calculationFailure.putValue("message", message);
                notifyListeners("measurementUpdate", calculationFailure);
                if (measurementResolved.compareAndSet(false, true)) {
                  reject(callback, message);
                }
                return;
              }

              ResultData result = new ResultData();

              Field[] fields = fatModel.getClass().getDeclaredFields();
              ResultData fieldsData = new ResultData();
              for (Field field : fields) {
                try {
                  field.setAccessible(true);
                  String fieldName = field.getName();
                  String publicFieldName = FieldKeyNormalizer.toPublicKey(fieldName);
                  Object fieldValue = field.get(fatModel);
                  Object publicFieldValue = FieldKeyNormalizer.toPublicValue(fieldValue);
                  fieldsData.putValue(publicFieldName, publicFieldValue);
                } catch (IllegalAccessException e) {
                  Log.e(TAG, "Failed to access field: " + field.getName(), e);
                }
              }

              result.putValue("fields", fieldsData);

              if (!measurementResolved.compareAndSet(false, true)) return;
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
            }
        }

        @Override
        public void monitorDataFail(@Nullable PPBodyBaseModel ppBodyBaseModel, @Nullable PPDeviceModel ppDeviceModel) {
          if (!measurementResolved.compareAndSet(false, true)) return;
          Log.e(TAG, "Data failed to process.");

          ResultData errorUpdate = new ResultData();
          errorUpdate.putValue("event", "dataFailure");
          errorUpdate.putValue("message", "Data failed to process.");
          notifyListeners("measurementUpdate", errorUpdate);
          activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
          reject(callback, "The scale could not produce a valid measurement. Keep full electrode contact and retry.");
        }
      });

      PPBleSendResultCallBack measurementStartCallback = new PPBleSendResultCallBack() {
        @Override
        public void onResult(PPScaleSendState sendState) {
          Log.d(TAG, "Measurement result: " + sendState);
        }
      };

      syncBorreUserForMeasurement(
        () -> startControllerMeasurement(measurementStartCallback),
        () -> {
          activity.runOnUiThread(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
          String message = "Could not synchronize the user profile with the Borre scale. Retry the measurement while the scale remains connected.";
          ResultData syncFailure = new ResultData();
          syncFailure.putValue("event", "dataFailure");
          syncFailure.putValue("message", message);
          notifyListeners("measurementUpdate", syncFailure);
          if (measurementResolved.compareAndSet(false, true)) {
            reject(callback, message);
          }
        }
      );
    } else {
      Log.e(TAG, "Controller not initialized.");
      reject(callback, "Controller not initialized.");
    }
  }
}
