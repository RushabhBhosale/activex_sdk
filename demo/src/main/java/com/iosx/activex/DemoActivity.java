package com.iosx.activex;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DemoActivity extends Activity {
  private static final int REQUEST_PERMISSIONS = 1001;

  private ActiveXScaleSDK sdk;
  private EditText ageInput;
  private EditText heightInput;
  private EditText uniquePatientIdInput;
  private EditText dateInput;
  private EditText patientNameInput;
  private EditText activeXSecretInput;
  private TextView activityLogView;
  private ScrollView activityLogScrollView;
  private boolean activeXInitialized = false;
  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sdk = new ActiveXScaleSDK(this);
    setContentView(createContentView());
    sdk.setEventListener(new LefuEventListener() {
      @Override
      public void onEvent(String eventName, ResultData data) {
        handleSdkEvent(eventName, data);
      }

      @Override
      public void onDeviceInfo(ResultData data) {
        Object deviceName = data == null ? null : data.get("deviceName");
        appendActivityLog(deviceName == null
          ? "Device connected"
          : "Device connected: " + deviceName);
      }
    });
    appendActivityLog("Demo ready — only " + LefuPlugin.SUPPORTED_DEVICE_NAME + " is supported");
    requestRuntimePermissions();
  }

  private View createContentView() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int padding = dp(16);
    root.setPadding(padding, padding, padding, padding);
    root.setBackgroundColor(0xFFF7F9FC);

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(18), dp(18), dp(18), dp(18));

    GradientDrawable headerBackground = new GradientDrawable();
    headerBackground.setColor(0xFF123B5D);
    headerBackground.setCornerRadius(dp(14));
    header.setBackground(headerBackground);

    TextView title = new TextView(this);
    title.setText("ActiveX SDK Demo");
    title.setTextSize(23);
    title.setTextColor(0xFFFFFFFF);
    header.addView(title, new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ));

    LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    headerParams.bottomMargin = dp(16);
    root.addView(header, headerParams);

    addActivityPanel(root);
    addSectionTitle(root, "Supported scale");
    addSupportedScaleLabel(root);

    addSectionTitle(root, "Patient profile");
    ageInput = addNumberInput(root, "Age", "30", false);
    heightInput = addNumberInput(root, "Height in cm", "175", true);

    addSectionTitle(root, "Measurement request");
    uniquePatientIdInput = addTextInput(root, "Unique Patient ID", "");
    dateInput = addTextInput(root, "Date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
    patientNameInput = addTextInput(root, "Patient Name", "");
    activeXSecretInput = addTextInput(root, "ActiveX Secret", "");
    activeXSecretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    addSectionTitle(root, "SDK actions");
    addButtonRow(root,
      "Request Permissions", v -> requestRuntimePermissions(),
      "Initialize ActiveX SDK", v -> initializeActiveXSdk());
    addButtonRow(root,
      "Sync User", v -> syncActiveXUser(),
      "Start ActiveX Scan", v -> startActiveXScan());
    addButtonRow(root,
      "Stop ActiveX Scan", v -> stopActiveXScan(),
      "Start Measurement", v -> startActiveXMeasurement());

    return wrapInScrollView(root);
  }

  private void addSectionTitle(LinearLayout root, String text) {
    TextView sectionTitle = new TextView(this);
    sectionTitle.setText(text);
    sectionTitle.setTextSize(13);
    sectionTitle.setTextColor(0xFF546E7A);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.topMargin = dp(14);
    root.addView(sectionTitle, params);
  }

  private void addActivityPanel(LinearLayout root) {
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(14), dp(12), dp(14), dp(12));

    GradientDrawable panelBackground = new GradientDrawable();
    panelBackground.setColor(0xFFFFFFFF);
    panelBackground.setCornerRadius(dp(10));
    panelBackground.setStroke(dp(1), 0xFFE1E7EC);
    panel.setBackground(panelBackground);

    addPanelTitle(panel, "Activity logs");
    activityLogView = new TextView(this);
    activityLogView.setTextSize(12);
    activityLogView.setTextColor(0xFF37474F);
    activityLogView.setTextIsSelectable(true);
    activityLogView.setPadding(0, dp(8), 0, dp(8));

    activityLogScrollView = new ActivityLogScrollView(this);
    activityLogScrollView.setFillViewport(false);
    activityLogScrollView.setVerticalScrollBarEnabled(true);
    activityLogScrollView.addView(activityLogView, new ScrollView.LayoutParams(
      ScrollView.LayoutParams.MATCH_PARENT,
      ScrollView.LayoutParams.WRAP_CONTENT
    ));
    panel.addView(activityLogScrollView, new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      dp(148)
    ));

    LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    panelParams.topMargin = dp(2);
    root.addView(panel, panelParams);
  }

  private static class ActivityLogScrollView extends ScrollView {
    ActivityLogScrollView(Context context) {
      super(context);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
      keepTouchInsideLog(event);
      boolean intercepted = super.onInterceptTouchEvent(event);
      releaseTouchAfterGesture(event);
      return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      keepTouchInsideLog(event);
      boolean handled = super.onTouchEvent(event);
      releaseTouchAfterGesture(event);
      return handled;
    }

    private void keepTouchInsideLog(MotionEvent event) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
        requestDisallowParentIntercept();
      }
    }

    private void releaseTouchAfterGesture(MotionEvent event) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(false);
        }
      }
    }

    private void requestDisallowParentIntercept() {
      ViewParent parent = getParent();
      if (parent != null) {
        parent.requestDisallowInterceptTouchEvent(true);
      }
    }
  }

  private TextView addPanelTitle(LinearLayout panel, String text) {
    TextView title = new TextView(this);
    title.setText(text);
    title.setTextSize(15);
    title.setTextColor(0xFF123B5D);
    panel.addView(title, new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ));
    return title;
  }

  private ScrollView wrapInScrollView(LinearLayout root) {
    ScrollView scrollView = new ScrollView(this);
    scrollView.setFillViewport(true);
    scrollView.addView(root);
    return scrollView;
  }

  private void addButtonRow(
    LinearLayout root,
    String firstText,
    View.OnClickListener firstListener,
    String secondText,
    View.OnClickListener secondListener
  ) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);

    row.addView(createButton(firstText, firstListener), buttonParams(true));
    row.addView(createButton(secondText, secondListener), buttonParams(false));

    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    rowParams.topMargin = dp(6);
    root.addView(row, rowParams);
  }

  private Button createButton(String text, View.OnClickListener listener) {
    Button button = new Button(this);
    button.setText(text);
    button.setTextSize(12);
    button.setAllCaps(false);
    button.setMinHeight(0);
    button.setMinWidth(0);
    button.setPadding(dp(4), 0, dp(4), 0);
    button.setOnClickListener(listener);
    return button;
  }

  private LinearLayout.LayoutParams buttonParams(boolean first) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      0,
      dp(44),
      1f
    );
    if (first) {
      params.rightMargin = dp(4);
    } else {
      params.leftMargin = dp(4);
    }
    return params;
  }

  private void addSupportedScaleLabel(LinearLayout root) {
    TextView scale = new TextView(this);
    scale.setText(LefuPlugin.SUPPORTED_DEVICE_NAME + " (Lefu Borre)");
    scale.setTextSize(16);
    scale.setTextColor(0xFF123B5D);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    params.topMargin = dp(8);
    root.addView(scale, params);
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

  private void syncActiveXUser() {
    try {
      int age = Integer.parseInt(ageInput.getText().toString().trim());
      double height = Double.parseDouble(heightInput.getText().toString().trim());
      sdk.syncLefuUserInfo(age, height, "male", statusCallback("User profile synchronized"));
    } catch (NumberFormatException e) {
      appendActivityLog("Enter a valid age and height");
    }
  }

  private void startActiveXMeasurement() {
    String uniquePatientId = uniquePatientIdInput.getText().toString().trim();
    String date = dateInput.getText().toString().trim();
    String patientName = patientNameInput.getText().toString().trim();
    String activeXSecret = activeXSecretInput.getText().toString().trim();

    if (uniquePatientId.isEmpty() || date.isEmpty() || patientName.isEmpty() || activeXSecret.isEmpty()) {
      appendActivityLog("Enter all measurement details");
      return;
    }

    MeasurementInput input = new MeasurementInput(
      uniquePatientId,
      date,
      patientName,
      activeXSecret
    );
    appendActivityLog("Starting " + LefuPlugin.SUPPORTED_DEVICE_NAME + " measurement; waiting for scale data...");
    sdk.startLefuMeasurement(this, input, new ResultCallback() {
      @Override
      public void onSuccess(ResultData data) {
        appendActivityLog("SDKPOST response received");
        showResponse(data);
      }

      @Override
      public void onError(String message, Throwable error) {
        appendActivityLog("SDKPOST request failed: " + message);
        showResponseError(message);
      }
    });
  }

  private void showResponse(ResultData response) {
    try {
      appendActivityLog("SDKPOST response\n" + new JSONObject(response).toString(2));
    } catch (Exception error) {
      appendActivityLog("SDKPOST response\n" + String.valueOf(response));
    }
  }

  private void showResponseError(String message) {
    appendActivityLog("Request failed: " + message);
  }

  private ResultCallback statusCallback(String successMessage) {
    return new ResultCallback() {
      @Override
      public void onSuccess(ResultData data) {
        appendActivityLog(successMessage);
      }

      @Override
      public void onError(String message, Throwable error) {
        appendActivityLog("SDK error: " + message);
      }
    };
  }

  private void handleSdkEvent(String eventName, ResultData data) {
    if ("lefuDeviceInfo".equals(eventName)) {
      String state = valueOf(data, "state");
      if ("discovered".equals(state)) {
        appendActivityLog("Scale discovered: " + deviceDescription(data) + "; attempting connection.");
      } else {
        appendActivityLog("Scanning for a device...");
      }
      return;
    }

    if ("scanState".equals(eventName)) {
      appendActivityLog("Scan " + valueOf(data, "state") + ": " + valueOf(data, "message"));
      return;
    }

    if ("connectionState".equals(eventName)) {
      String device = deviceDescription(data);
      appendActivityLog(
        "Connection " + valueOf(data, "state") + ": " + valueOf(data, "message")
          + (device.isEmpty() ? "" : " [" + device + "]")
      );
      return;
    }

    if ("deviceDisconnected".equals(eventName)) {
      appendActivityLog("Connection disconnected: " + valueOf(data, "message"));
      return;
    }

    if (!"measurementUpdate".equals(eventName)) return;

    String event = data == null ? "" : String.valueOf(data.get("event"));
    switch (event) {
      case "scaleState":
        appendActivityLog("Scale state: " + valueOf(data, "state"));
        break;
      case "measurementStarted":
        appendActivityLog("Measurement data started");
        break;
      case "bodyFatMeasurementStarted":
        appendActivityLog("Body composition calculation started");
        break;
      case "heartRateMeasuring":
        appendActivityLog("Measuring heart rate");
        break;
      case "measurementComplete":
        appendActivityLog("Measurement complete; waiting for SDKPOST");
        break;
      case "dataFailure":
        appendActivityLog("Measurement data failed: " + valueOf(data, "message"));
        break;
      case "measurementStop":
        appendActivityLog("Measurement stopped because the scale disconnected");
        break;
      case "overWeight":
        appendActivityLog("Scale reported an over-weight condition");
        break;
      case "measurementAuthorizationFailed":
        appendActivityLog("SDKPOST authorization failed");
        break;
      default:
        break;
    }
  }

  private String valueOf(ResultData data, String key) {
    if (data == null || data.get(key) == null) return "unknown";
    return String.valueOf(data.get(key));
  }

  private String deviceDescription(ResultData data) {
    if (data == null) return "";
    Object name = data.get("deviceName");
    Object address = data.get("deviceAddress");
    if (name == null && address == null) return "";
    if (name == null) return String.valueOf(address);
    if (address == null) return String.valueOf(name);
    return name + " (" + address + ")";
  }

  private void appendActivityLog(String message) {
    runOnUiThread(() -> {
      if (activityLogView == null) return;
      String line = timeFormat.format(new Date()) + "  " + message + "\n";
      activityLogView.append(line);
      activityLogScrollView.post(() -> activityLogScrollView.fullScroll(View.FOCUS_DOWN));
    });
  }

  private void initializeActiveXSdk() {
    appendActivityLog("Initializing SDK...");
    sdk.initializeLefu(new ResultCallback() {
      @Override
      public void onSuccess(ResultData data) {
        activeXInitialized = true;
        appendActivityLog("SDK initialized");
      }

      @Override
      public void onError(String message, Throwable error) {
        activeXInitialized = false;
        appendActivityLog("SDK initialization failed: " + message);
      }
    });
  }

  private void startActiveXScan() {
    if (!hasRequiredPermissions()) {
      appendActivityLog("Bluetooth permissions are required");
      requestRuntimePermissions();
      return;
    }

    BluetoothAdapter adapter = getBluetoothAdapter();
    if (adapter == null) {
      appendActivityLog("Bluetooth is unavailable");
      return;
    }
    if (!adapter.isEnabled()) {
      appendActivityLog("Bluetooth is turned off");
      return;
    }

    boolean locationEnabled = isLocationEnabled();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !locationEnabled) {
      appendActivityLog("Location services are turned off");
      return;
    }
    if (!activeXInitialized) {
      appendActivityLog("Initializing SDK before scanning");
      sdk.initializeLefu(new ResultCallback() {
        @Override
        public void onSuccess(ResultData data) {
          activeXInitialized = true;
          appendActivityLog("SDK initialized");
          sdk.startLefuScan(statusCallback("Scanning started"));
        }

        @Override
        public void onError(String message, Throwable error) {
          activeXInitialized = false;
          appendActivityLog("SDK initialization failed: " + message);
        }
      });
      return;
    }

    appendActivityLog("Starting " + LefuPlugin.SUPPORTED_DEVICE_NAME + " scan...");
    sdk.startLefuScan(statusCallback("Scanning started"));
  }

  private void stopActiveXScan() {
    sdk.stopLefuScan(statusCallback("Scan stopped"));
  }

  private void requestRuntimePermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      appendActivityLog("Runtime permissions not required");
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
      appendActivityLog("Bluetooth permissions ready");
      return;
    }

    appendActivityLog("Requesting Bluetooth permissions");
    requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode != REQUEST_PERMISSIONS) return;

    boolean allGranted = grantResults.length > 0;
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        allGranted = false;
        break;
      }
    }
    appendActivityLog(allGranted
      ? "Bluetooth permissions granted"
      : "Bluetooth permissions denied");
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

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
