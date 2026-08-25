# ActiveX Android SDK Integration Guide

**Document version:** 1.0  
**Last updated:** 25 August 2026<br>
**SDK artifact:** `app-release.aar`  
**Primary package:** `com.iosx.activex`

## 1. Purpose

This guide describes the complete process for integrating the ActiveX Android SDK into a client application that connects to the `CF650_BG` Bluetooth body-composition scale and returns authorized body-composition measurements.

The current client integration exposes one hardware flow:

- **CF650_BG through Lefu Borre**: exact-name discovery, connection, user synchronization, and measurement.

The standalone demo and SDK facade intentionally use only this device. Other Lefu models, Ice Lefu, and Jambul flows are not supported by this build.

## 2. Integration flow

The application should execute this sequence:

```text
Add AAR and dependencies
        ↓
Add manifest permissions
        ↓
Request runtime Bluetooth/location permissions
        ↓
Create ActiveXScaleSDK and register listeners
        ↓
Initialize the Lefu Borre flow
        ↓
Sync age, height, and sex
        ↓
Scan and connect to the scale
        ↓
Create MeasurementInput
        ↓
Start the measurement
        ↓
Receive events and the authorized result
```

Do not start a measurement before initialization, user synchronization, and device connection are complete.

## 3. Requirements

- Android Studio with a physical Android device for Bluetooth testing.
- JDK 17 for the supplied Gradle configuration.
- `compileSdk` 35 or higher.
- Minimum Android API level 22 for the current SDK build.
- Bluetooth Low Energy support.
- A `CF650_BG` Lefu Borre scale. Other advertised device names are ignored.
- A valid ActiveX authorization secret.

Bluetooth testing should be performed on a real device. An Android emulator cannot reliably reproduce scale discovery and connection behavior.

## 4. Add the SDK AAR

Create a `libs` directory inside the client application module and copy the supplied AAR into it:

```text
client-app/
└── app/
    ├── libs/
    │   └── app-release.aar
    └── build.gradle
```

Use the AAR supplied by ActiveX for the target environment. Do not unzip or modify the AAR.

## 5. Configure Gradle

### 5.1 Add repositories

For a modern project using `settings.gradle`, add the local AAR directory and the standard Android repositories:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        flatDir {
            dirs "$rootDir/app/libs"
        }
    }
}
```

If the project already defines these repositories, keep the existing convention and add only the missing entries.

### 5.2 Add the AAR and AndroidX dependencies

Add the following to the client application's `app/build.gradle`:

```groovy
dependencies {
    implementation(files("libs/app-release.aar"))

    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.coordinatorlayout:coordinatorlayout:1.2.0"
    implementation "androidx.core:core-splashscreen:1.0.1"
    implementation "androidx.constraintlayout:constraintlayout:2.1.4"
    implementation "androidx.localbroadcastmanager:localbroadcastmanager:1.0.0"
}
```

The supplied AAR contains the complete Lefu scale runtime. Do not add separate vendor scale, Bluetooth, calculation, or serialization dependencies to the client application.

The client application may still use its normal AndroidX dependencies. The AAR contains the vendor runtime classes, calculation assets, Bluetooth assets, native calculation libraries, and consumer ProGuard rules.

Use the following Android configuration as the baseline:

```groovy
android {
    compileSdk 35

    defaultConfig {
        minSdk 22
        targetSdk 35
    }
}
```

Sync the project and confirm that Gradle resolves all dependencies before continuing.

## 6. Add manifest permissions

Add these permissions to the client application's `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Android 11 and below -->
    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />

    <!-- Android 12 and above -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

    <!-- BLE discovery and older Android behavior -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- SDK authorization and supporting SDK behavior -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="false" />
</manifest>
```

The AAR contributes supporting manifest entries, including its `FileProvider`. Do not remove provider entries during manifest cleanup unless ActiveX supplies an alternative configuration.

### Cleartext network warning

The current SDK source uses the development URL:

```text
http://dev-api.myactivex.com/external/SdkDataPost
```

The current AAR also contributes `android:usesCleartextTraffic="true"`. This is not suitable for production. Before release, obtain confirmation from ActiveX that the supplied AAR uses the production HTTPS endpoint. The production application should not enable cleartext traffic unless explicitly approved.

## 7. Request runtime permissions

Manifest permissions alone are not enough.

### Android 12 and above

Request:

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_ADVERTISE`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

### Android 6 through Android 11

Request:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

Also confirm that Bluetooth is enabled. On Android 6–11, Location services should be enabled because many vendors require them for BLE discovery. On Android 12+, some vendor Bluetooth stacks still behave better when Location services are enabled.

Example Java code:

```java
private static final int REQUEST_BLE_PERMISSIONS = 1001;

private void requestBlePermissions() {
    List<String> permissions = new ArrayList<>();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    List<String> missing = new ArrayList<>();
    for (String permission : permissions) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    if (!missing.isEmpty()) {
        ActivityCompat.requestPermissions(
            this,
            missing.toArray(new String[0]),
            REQUEST_BLE_PERMISSIONS
        );
    }
}
```

Do not start scanning until permissions are granted. If a permission is denied, explain why it is required and provide a route to application settings when necessary.

## 8. Create the SDK instance and listener

Create one SDK instance for the screen or application flow that owns the measurement session:

```java
private ActiveXScaleSDK activeXScaleSDK;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    activeXScaleSDK = new ActiveXScaleSDK(this);
    activeXScaleSDK.setEventListener(new LefuEventListener() {
        @Override
        public void onEvent(String eventName, ResultData data) {
            handleSdkEvent(eventName, data);
        }

        @Override
        public void onDeviceInfo(ResultData data) {
            handleConnectedDevice(data);
        }
    });
}
```

The `ActiveXScaleSDK` facade exposes the Lefu flow used by this client guide:

```java
activeXScaleSDK.initializeLefu(callback("initializeLefu"));
```

The client does not need to pass the Lefu app key or app secret. Those are configured internally by the SDK build.

## 9. Implement callbacks

All public operations use `ResultCallback`:

```java
private ResultCallback callback(String operation) {
    return new ResultCallback() {
        @Override
        public void onSuccess(ResultData data) {
            runOnUiThread(() ->
                Log.d("ActiveX", operation + " succeeded: " + data)
            );
        }

        @Override
        public void onError(String message, Throwable error) {
            runOnUiThread(() -> {
                Log.e("ActiveX", operation + " failed: " + message, error);
                showMeasurementError(message);
            });
        }
    };
}
```

Bluetooth and authorization callbacks may be delivered from a background thread. Update views and application state on the main thread.

`ResultData` extends `HashMap<String, Object>`. Read values by key and do not assume every optional value exists.

## 10. Initialize the Lefu flow

Call initialization once before scanning:

```java
activeXScaleSDK.initializeLefu(callback("initializeLefu"));
```

On success, the Lefu Bluetooth and calculation components are initialized. On failure, do not scan; show the error and allow retry.

## 11. Synchronize user information

The calculation engine needs the user's profile:

```java
int ageYears = 30;
double heightCm = 175.0;
String sex = "male"; // Use "male" or "female".

activeXScaleSDK.syncLefuUserInfo(
    ageYears,
    heightCm,
    sex,
    callback("syncLefuUserInfo")
);
```

Rules:

- `age` is required.
- `height` is required and is interpreted as centimeters by the current demo flow.
- `sex` is required. Use `male` or `female`.
- Re-sync the profile when the selected patient changes.
- Validate values in the client before calling the SDK.

## 12. Scan and connect to a Lefu scale

Start scanning after permissions, initialization, and user synchronization:

```java
activeXScaleSDK.startLefuScan(callback("startLefuScan"));
```

The current Lefu implementation scans for up to five minutes and automatically connects only when the advertised device name is exactly `CF650_BG`. All other BLE and Lefu advertisements are ignored.

The scan callback may first return:

```text
connected = false
message = Scanning started. Waiting for devices...
```

When a device is found, the SDK emits device information. Use `onDeviceInfo` to mark the device as connected and enable the measurement action:

```java
private void handleConnectedDevice(ResultData data) {
    String deviceName = String.valueOf(data.get("deviceName"));
    String deviceAddress = String.valueOf(data.get("deviceAddress"));

    runOnUiThread(() -> {
        deviceStatusText.setText("Connected: " + deviceName);
        measurementButton.setEnabled(true);
    });
}
```

Stop discovery when the user cancels or leaves the discovery screen:

```java
activeXScaleSDK.stopLefuScan(callback("stopLefuScan"));
```

Optional methods:

```java
activeXScaleSDK.checkLefuConnection(callback("checkLefuConnection"));
activeXScaleSDK.getLefuDevices(callback("getLefuDevices"));
```

## 13. Create MeasurementInput

Every measurement must include a `MeasurementInput`:

| Field | Required | Description |
| --- | --- | --- |
| `uniquePatientId` | Yes | Stable patient identifier from the client system. |
| `date` | Yes | Measurement date. Use `yyyy-MM-dd` unless ActiveX provides another contract. |
| `patientName` | Yes | Patient name associated with the measurement. |
| `activeXSecret` | Yes | ActiveX-issued authorization secret. |

Example:

```java
String uniquePatientId = patient.getExternalId();
String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
String patientName = patient.getDisplayName();
String activeXSecret = secureSecretProvider.getActiveXSecret();

MeasurementInput measurementInput = new MeasurementInput(
    uniquePatientId,
    date,
    patientName,
    activeXSecret
);
```

Security requirements:

- Do not hardcode `activeXSecret` in application source.
- Do not display it in the user interface.
- Do not store it in plain text logs, analytics, screenshots, or crash reports.
- Prefer retrieving it from the client's authenticated backend or secure configuration service.

The SDK sends `activeXSecret` as the `x-api-key` request header. It is not included in the measurement JSON body and is not returned to the caller.

## 14. Start the measurement

The SDK requires an Android `Activity` because it keeps the screen awake during measurement:

```java
activeXScaleSDK.startLefuMeasurement(
    this,
    measurementInput,
    new ResultCallback() {
        @Override
        public void onSuccess(ResultData data) {
            handleAuthorizedMeasurement(data);
        }

        @Override
        public void onError(String message, Throwable error) {
            handleMeasurementFailure(message, error);
        }
    }
);
```

Before calling this method, confirm:

1. Bluetooth and location permissions are granted.
2. Bluetooth is enabled.
3. Lefu initialization succeeded.
4. User information was synchronized.
5. A scale is connected.
6. All `MeasurementInput` values are non-empty.

The success callback is delivered only after a completed measurement and successful authorization.

## 15. Handle measurement events

Use `LefuEventListener` for progress state. Do not treat an intermediate event as a completed measurement.

```java
private void handleSdkEvent(String eventName, ResultData data) {
    switch (eventName) {
        case "lefuDeviceInfo":
            // Device discovered; connection may still be in progress.
            break;
        case "measurementUpdate":
            handleMeasurementUpdate(data);
            break;
        case "deviceDisconnected":
            showDeviceDisconnectedState();
            break;
        default:
            Log.d("ActiveX", "Unhandled SDK event: " + eventName + " " + data);
    }
}

private void handleMeasurementUpdate(ResultData data) {
    String event = String.valueOf(data.get("event"));

    switch (event) {
        case "measurementStarted":
        case "bodyFatMeasurementStarted":
        case "heartRateMeasuring":
            showMeasurementInProgress(event);
            break;
        case "measurementComplete":
            showMeasurementCalculationState();
            break;
        case "dataFailure":
        case "measurementAuthorizationFailed":
            showMeasurementError(String.valueOf(data.get("message")));
            break;
        default:
            Log.d("ActiveX", "Unhandled measurement event: " + data);
    }
}
```

Common Lefu events:

| Callback | Event | Meaning |
| --- | --- | --- |
| `onEvent` | `lefuDeviceInfo` | A scale was discovered and connection handling has started. |
| `onEvent` | `measurementStarted` | Measurement data has started arriving. |
| `onEvent` | `bodyFatMeasurementStarted` | Impedance/body-fat processing has started. |
| `onEvent` | `heartRateMeasuring` | Heart-rate data is being collected. |
| `onEvent` | `measurementComplete` | Scale data is locked and calculation is being completed. |
| `onEvent` | `dataFailure` | The SDK could not process the measurement data. |
| `onEvent` | `measurementAuthorizationFailed` | The completed result was rejected or could not be authorized. |
| `onEvent` | `deviceDisconnected` | The connected scale disconnected. |
| `onDeviceInfo` | device data | The SDK reports connected-device information. |

## 16. Handle the measurement result

The successful `ResultData` contains only the parsed JSON response returned by `SdkDataPost`. The raw scale calculation is used internally to build the authorization request and is not returned to the client callback.

The response keys and nesting are controlled by the API contract. The SDK preserves the response object recursively. If the endpoint returns a JSON array, primitive, or non-JSON body, it is exposed under the `response` key.

Example:

```java
private void handleAuthorizedMeasurement(ResultData response) {
    showMeasurementComplete(response);
}
```

The authorization request sends the calculated fields at the JSON root together with patient metadata:

```json
{
  "weightKg": 72.4,
  "bodyFatRate": 18.2,
  "UniquePatientId": "patient-123",
  "Date": "2026-07-23",
  "PatientName": "Example Patient"
}
```

The `activeXSecret` is sent only in the `x-api-key` header.

## 17. Lifecycle and cleanup

Recommended behavior:

1. Create the SDK instance when the measurement screen is created.
2. Register the listener before initialization or scanning.
3. Stop scanning when the user cancels discovery or leaves the screen.
4. Disable the measurement button while measuring.
5. Re-enable it after success or failure.
6. Do not create multiple SDK instances for one active session.
7. Save the final result only after `onSuccess`.

## 18. Error handling

| Error or symptom | Likely cause | Recommended action |
| --- | --- | --- |
| No scale appears | Bluetooth is off, permissions are missing, Location is off, or the scale is asleep | Turn on Bluetooth, grant permissions, enable Location where required, wake the scale, and retry nearby. |
| `Controller not initialized.` | Measurement started before a successful scan/connection | Complete initialization and scan first. |
| `User info missing. Call syncUserInfo first.` | User data was not synchronized | Call the relevant sync method before measurement. |
| `Measurement input is required.` | No `MeasurementInput` was supplied | Create a valid input with all four values. |
| Required input error | Patient ID, date, name, or secret is blank | Validate values before starting measurement. |
| `measurementAuthorizationFailed` | Secret invalid, endpoint rejected, network unavailable, or response not authorized | Check the secret, endpoint, network, and ActiveX environment. |
| Fields disappear in release | R8 removed classes or fields accessed through reflection | Temporarily disable minification or obtain ActiveX-approved keep rules. |
| APK says package is invalid | An unsigned APK is being installed | Install a signed APK; never distribute `*-unsigned.apk`. |
| APK will not update an existing app | Existing package has a different signing certificate | Uninstall the old test app once, then install the correctly signed build. |

## 19. R8 and release builds

The SDK reflects over calculated model fields to produce the public result. For initial integration:

```groovy
buildTypes {
    release {
        minifyEnabled false
        shrinkResources false
    }
}
```

If minification is required, request production keep rules from ActiveX and verify that the complete result field set is unchanged between debug and release builds. A successful build alone does not prove that reflected measurement fields are preserved.

## 20. Production security checklist

Before production rollout, confirm with ActiveX:

- The AAR uses the production authorization endpoint, not the development URL.
- The authorization endpoint uses HTTPS.
- Cleartext traffic is disabled unless explicitly required.
- Disable `SdkDataPost` payload/response logging in the production AAR after integration verification. The `x-api-key` value is never written to Logcat.
- `activeXSecret` is obtained securely and never hardcoded or logged.
- The client release APK is signed with the client's release keystore.
- R8/minification behavior has been tested if enabled.
- Android 12, Android 13, and supported OEM devices have been tested.
- Results are persisted only after authorization succeeds.
- Disconnection, permission denial, timeout, network failure, and authorization rejection are visible to the user.

## 21. Build and verify the supplied demo

The repository contains the SDK library under `android/` and the standalone demo under `demo/`.

### Rebuild the SDK AAR

From the repository root:

```bash
cd android
./gradlew :app:assembleRelease
```

Generated AAR:

```text
android/app/build/outputs/aar/app-release.aar
```

Copy the generated AAR into `demo/activeXSdk.aar` before building the demo.

### Build the demo

```bash
cd demo
./gradlew assembleDebug
```

Debug APK:

```text
demo/build/outputs/apk/debug/ActiveXScaleSDKDemo-debug.apk
```

Signed local release APK:

```bash
cd demo
./gradlew assembleRelease
```

Release APK:

```text
demo/build/outputs/apk/release/ActiveXScaleSDKDemo-release.apk
```

Do not install `ActiveXScaleSDKDemo-release-unsigned.apk`. If an older demo signed with a different certificate is installed, uninstall it once before installing the new build.

## 22. Public API reference

### `ActiveXScaleSDK`

| Method | Purpose |
| --- | --- |
| `new ActiveXScaleSDK(Context)` | Creates the SDK facade. |
| `setEventListener(LefuEventListener)` | Registers device and measurement event callbacks. |
| `initializeLefu(ResultCallback)` | Initializes the `CF650_BG` Lefu Borre flow. |
| `startLefuScan(ResultCallback)` | Scans for and automatically connects only to `CF650_BG`. |
| `stopLefuScan(ResultCallback)` | Stops Lefu discovery. |
| `checkLefuConnection(ResultCallback)` | Returns Lefu connection state. |
| `getLefuDevices(ResultCallback)` | Returns devices discovered by Lefu. |
| `connectLefuDevice(String, String, ResultCallback)` | Connects by address only when the supplied name is exactly `CF650_BG`. |
| `syncLefuUserInfo(Integer, Double, String, ResultCallback)` | Sets the Lefu calculation profile. |
| `startLefuMeasurement(Activity, MeasurementInput, ResultCallback)` | Starts an authorized Lefu measurement. |

Legacy Ice Lefu and Jambul facade signatures remain present for source compatibility, but every such call reports `This SDK build only supports CF650_BG through the Lefu Borre flow.` They cannot initialize, scan, connect, or measure in this build.

The overloads that omit `MeasurementInput` are retained for source compatibility, but the current measurement implementation requires authorization input. Use the overload that accepts `MeasurementInput`.

### `ResultCallback`

```java
public interface ResultCallback {
    void onSuccess(ResultData data);
    void onError(String message, Throwable error);
}
```

### `LefuEventListener`

```java
public interface LefuEventListener {
    void onEvent(String eventName, ResultData data);
    void onDeviceInfo(ResultData data);
}
```

### `MeasurementInput`

```java
public MeasurementInput(
    String uniquePatientId,
    String date,
    String patientName,
    String activeXSecret
)
```

All four constructor values are required and must not be blank.

## 23. Client acceptance checklist

The integration is ready for acceptance when:

- The client app builds from a clean checkout.
- The AAR and the client's standard AndroidX dependencies resolve without duplicate-class errors.
- Runtime permissions are granted.
- Bluetooth discovery finds the target scale.
- The scale connects successfully.
- The selected patient's age, height, and sex are synchronized.
- A completed measurement returns an authorized `ResultData`.
- The result is saved by the client backend.
- Authorization rejection is shown as an error and is not saved as successful data.
- Device disconnection and retry are handled.
- Endpoint, logging, secret handling, signing, and R8 configuration are approved for production.
