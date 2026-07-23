package com.iosx.activex;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DemoActivity extends Activity {
  private static final int REQUEST_PERMISSIONS = 1001;

  private ActiveXScaleSDK sdk;
  private TextView logView;
  private EditText ageInput;
  private EditText heightInput;
  private EditText uniquePatientIdInput;
  private EditText dateInput;
  private EditText patientNameInput;
  private EditText activeXSecretInput;
  private boolean lefuInitialized = false;
  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sdk = new ActiveXScaleSDK(this);
    sdk.setEventListener(new LefuEventListener() {
      @Override
      public void onEvent(String eventName, ResultData data) {
        appendLog("event " + eventName + ": " + data);
      }

      @Override
      public void onDeviceInfo(ResultData data) {
        appendLog("deviceInfo: " + data);
      }
    });

    setContentView(createContentView());
    requestRuntimePermissions();
  }

  private View createContentView() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int padding = dp(16);
    root.setPadding(padding, padding, padding, padding);

    TextView title = new TextView(this);
    title.setText("ActiveX Lefu Demo");
    title.setTextSize(22);
    title.setTextColor(0xFF212121);
    root.addView(title, new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ));

    ageInput = addNumberInput(root, "Age", "30", false);
    heightInput = addNumberInput(root, "Height in cm", "175", true);
    uniquePatientIdInput = addTextInput(root, "Unique Patient ID", "");
    dateInput = addTextInput(root, "Date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
    patientNameInput = addTextInput(root, "Patient Name", "");
    activeXSecretInput = addTextInput(root, "ActiveX Secret", "");
    activeXSecretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    addButton(root, "Request Permissions", v -> requestRuntimePermissions());
    addButton(root, "Initialize Lefu SDK", v -> initializeLefuSdk());
    addButton(root, "Sync User", v -> syncLefuUser());
    addButton(root, "Start Lefu Scan", v -> startLefuScan());
    addButton(root, "Stop Lefu Scan", v -> sdk.stopLefuScan(callback("stopLefuScan")));
    addButton(root, "Start Lefu Measurement", v -> startLefuMeasurement());

    logView = new TextView(this);
    logView.setTextSize(13);
    logView.setTextColor(0xFF263238);
    logView.setText("Ready.\n");

    ScrollView scrollView = new ScrollView(this);
    scrollView.addView(logView);
    LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      0
    );
    logParams.weight = 1;
    logParams.topMargin = dp(12);
    root.addView(scrollView, logParams);

    return root;
  }

  private void addButton(LinearLayout root, String text, View.OnClickListener listener) {
    Button button = new Button(this);
    button.setText(text);
    button.setAllCaps(false);
    button.setOnClickListener(listener);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.topMargin = dp(8);
    root.addView(button, params);
  }

  private EditText addNumberInput(LinearLayout root, String hint, String value, boolean decimal) {
    EditText input = new EditText(this);
    input.setHint(hint);
    input.setText(value);
    input.setSingleLine(true);
    input.setInputType(decimal
      ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
      : InputType.TYPE_CLASS_NUMBER);

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.topMargin = dp(8);
    root.addView(input, params);
    return input;
  }

  private EditText addTextInput(LinearLayout root, String hint, String value) {
    EditText input = new EditText(this);
    input.setHint(hint);
    input.setText(value);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT);

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.topMargin = dp(8);
    root.addView(input, params);
    return input;
  }

  private void syncLefuUser() {
    try {
      int age = Integer.parseInt(ageInput.getText().toString().trim());
      double height = Double.parseDouble(heightInput.getText().toString().trim());
      sdk.syncLefuUserInfo(age, height, "male", callback("syncLefuUserInfo"));
    } catch (NumberFormatException e) {
      appendLog("syncLefuUserInfo error: enter valid age and height");
    }
  }

  private void startLefuMeasurement() {
    String uniquePatientId = uniquePatientIdInput.getText().toString().trim();
    String date = dateInput.getText().toString().trim();
    String patientName = patientNameInput.getText().toString().trim();
    String activeXSecret = activeXSecretInput.getText().toString().trim();

    if (uniquePatientId.isEmpty() || date.isEmpty() || patientName.isEmpty() || activeXSecret.isEmpty()) {
      appendLog("startLefuMeasurement blocked: enter patient ID, date, name, and ActiveX secret.");
      return;
    }

    MeasurementInput input = new MeasurementInput(
      uniquePatientId,
      date,
      patientName,
      activeXSecret
    );
    sdk.startLefuMeasurement(this, input, callback("startLefuMeasurement"));
  }

  private void initializeLefuSdk() {
    sdk.initializeLefu(new ResultCallback() {
      @Override
      public void onSuccess(ResultData data) {
        lefuInitialized = true;
        appendLog("initializeLefu success: " + data);
      }

      @Override
      public void onError(String message, Throwable error) {
        lefuInitialized = false;
        appendLog("initializeLefu error: " + message);
      }
    });
  }

  private void startLefuScan() {
    if (!hasRequiredPermissions()) {
      appendLog("Start scan blocked: missing Bluetooth/location permission.");
      requestRuntimePermissions();
      return;
    }

    BluetoothAdapter adapter = getBluetoothAdapter();
    if (adapter == null) {
      appendLog("Start scan blocked: this device has no Bluetooth adapter.");
      return;
    }
    if (!adapter.isEnabled()) {
      appendLog("Start scan blocked: turn on Bluetooth.");
      return;
    }

    boolean locationEnabled = isLocationEnabled();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !locationEnabled) {
      appendLog("Start scan blocked: turn on Location services for BLE scanning.");
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !locationEnabled) {
      appendLog("Location services are off. Android 12+ usually allows BLE scan, but some vendor stacks still filter results.");
    }
    if (!lefuInitialized) {
      appendLog("Lefu SDK has not been initialized yet. Initializing before scan...");
      sdk.initializeLefu(new ResultCallback() {
        @Override
        public void onSuccess(ResultData data) {
          lefuInitialized = true;
          appendLog("initializeLefu success: " + data);
          sdk.startLefuScan(callback("startLefuScan"));
        }

        @Override
        public void onError(String message, Throwable error) {
          lefuInitialized = false;
          appendLog("initializeLefu error: " + message);
        }
      });
      return;
    }

    appendLog("Starting Lefu scan. Keep the scale awake and nearby.");
    sdk.startLefuScan(callback("startLefuScan"));
  }

  private ResultCallback callback(String operation) {
    return new ResultCallback() {
      @Override
      public void onSuccess(ResultData data) {
        appendLog(operation + " success: " + data);
      }

      @Override
      public void onError(String message, Throwable error) {
        appendLog(operation + " error: " + message);
      }
    };
  }

  private void requestRuntimePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      appendLog("Runtime permissions not required for this Android version.");
      return;
    }

    List<String> permissions = getRequiredPermissions();

    List<String> missing = new ArrayList<>();
    for (String permission : permissions) {
      if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        missing.add(permission);
      }
    }

    if (missing.isEmpty()) {
      appendLog("Permissions already granted.");
    } else {
      requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
    }
  }

  private boolean hasRequiredPermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true;
    }

    for (String permission : getRequiredPermissions()) {
      if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private List<String> getRequiredPermissions() {
    List<String> permissions = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(Manifest.permission.BLUETOOTH_SCAN);
      permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
      permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
    }
    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    return permissions;
  }

  private BluetoothAdapter getBluetoothAdapter() {
    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    return bluetoothManager != null ? bluetoothManager.getAdapter() : BluetoothAdapter.getDefaultAdapter();
  }

  private boolean isLocationEnabled() {
    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    if (locationManager == null) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return locationManager.isLocationEnabled();
    }
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
      || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQUEST_PERMISSIONS) return;

    for (int i = 0; i < permissions.length; i++) {
      boolean granted = i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED;
      appendLog("permission " + permissions[i] + ": " + (granted ? "granted" : "denied"));
    }
  }

  private void appendLog(String message) {
    runOnUiThread(() -> {
      String line = timeFormat.format(new Date()) + "  " + message + "\n";
      logView.append(line);
    });
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
