package com.assistant.main.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http2.Header;

/**
 * A single-file, self-contained Tuya API client for Android, faithfully reproducing the logic
 * from the provided JavaScript example.
 *
 * <p><b>Usage:</b>
 * <pre>
 * // 1. Get the singleton instance in your Activity or Fragment
 * TuyaApiClient apiClient = TuyaApiClient.getInstance(getApplicationContext());
 *
 * // 2. Login
 * apiClient.login("user@email.com", "password", "EU", new TuyaApiClient.SmartLifeCallback<Boolean>() {
 *     @Override
 *     public void onSuccess(Boolean result) {
 *         // 3. On successful login, get devices
 *         apiClient.getDeviceList(new TuyaApiClient.SmartLifeCallback<List<TuyaApiClient.Device>>() {
 *             @Override
 *             public void onSuccess(List<TuyaApiClient.Device> devices) {
 *                 Log.d("TuyaDemo", "Found devices: " + devices.size());
 *             }
 *             @Override
 *             public void onFailure(Exception e) {
 *                 Log.e("TuyaDemo", "Failed to get devices", e);
 *             }
 *         });
 *     }
 *     @Override
 *     public void onFailure(Exception e) {
 *         Log.e("TuyaDemo", "Login failed", e);
 *     }
 * });
 * </pre>
 */
public class SmartLife {

    private static final String TAG = "SmartLifeClient";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    // Static maps to replicate the JS constants
    private static final Map<String, String> REGION_BASE_URL;
    private static final Map<String, String> REGION_VALUES;

    static {
        Map<String, String> baseUrlMap = new HashMap<>();
        baseUrlMap.put("EU", "https://px1.tuyaeu.com");
        baseUrlMap.put("US", "https://px1.tuyaus.com");
        baseUrlMap.put("CN", "https://openapi.tuya.cn");
        REGION_BASE_URL = Collections.unmodifiableMap(baseUrlMap);

        Map<String, String> regionValueMap = new HashMap<>();
        regionValueMap.put("EU", "44");
        regionValueMap.put("US", "1");
        regionValueMap.put("CN", "86");
        REGION_VALUES = Collections.unmodifiableMap(regionValueMap);
    }

    // Singleton instance
    private static volatile SmartLife instance;

    // Instance members
    private final OkHttpClient client;
    private final Gson gson;
    private final SharedPreferences storage;
    private final Handler mainThreadHandler;
    private final ExecutorService executor;

    // State object, equivalent to the JS `userInfo`
    private UserInfo userInfo;

    // =================================================================================
    // Data Models (Public Static Nested Classes)
    // =================================================================================

    /**
     * Holds all user state, including tokens and devices.
     * Equivalent to the `userInfo` object in the JavaScript code.
     */
    public static class UserInfo {
        @SerializedName("access_token")
        public String accessToken = "";

        @SerializedName("refresh_token")
        public String refreshToken = "";

        @SerializedName("expires_in")
        public long expiresIn = 0; // Stored as a future timestamp in millis

        @SerializedName("devices")
        public List<Device> devices = new ArrayList<>();

        @SerializedName("logged_in")
        public boolean loggedIn = false;

        @SerializedName("baseUrl")
        public String baseUrl = "";

        /**
         * Checks if the access token is present and has not expired.
         */
        public boolean isTokenValid() {
            return accessToken != null && !accessToken.isEmpty() && expiresIn > System.currentTimeMillis();
        }
    }

    /**
     * Represents a single Tuya device.
     */
    public static class Device {
        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;

        @SerializedName("data")
        public Map<String, Object> data; // Flexible map for various device states

        @SerializedName("dev_type")
        public String dev_type;

        @SerializedName("ha_type")
        public String ha_type;
    }

    // Models for API responses
    private static class AuthResponse {
        @SerializedName("access_token") String accessToken;
        @SerializedName("refresh_token") String refreshToken;
        @SerializedName("expires_in") long expiresInSeconds;
    }

    private static class SkillHeader {
        @SerializedName("name") String name;
        @SerializedName("namespace") String namespace;
        @SerializedName("payloadVersion") int payloadVersion = 1;
        @SerializedName("code") String code; // Only in response
    }

    private static class SkillPayload {
        @SerializedName("accessToken") String accessToken;
        //@SerializedName("devId") String devId; // Only for control actions
    }

    private static class DiscoveryPayload extends SkillPayload {
        // No extra fields needed
    }

    private static class SkillRequest<T> {
        @SerializedName("header") SkillHeader header;
        @SerializedName("payload") T payload;
    }

    private static class SkillResponsePayload {
        @SerializedName("devices") List<Device> devices;
    }

    private static class SkillResponse {
        @SerializedName("header") SkillHeader header;
        @SerializedName("payload") SkillResponsePayload payload;
    }

    /**
     * Generic callback interface for handling asynchronous API results.
     *
     * @param <T> The type of the successful result.
     */
    public interface SmartLifeCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }


    // =================================================================================
    // Singleton Constructor and Initializer
    // =================================================================================

    private SmartLife(Context context) {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.mainThreadHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();

        // Android's SharedPreferences is the equivalent of node-persist for simple storage
        this.storage = context.getApplicationContext().getSharedPreferences("SmartLifePrefs", Context.MODE_PRIVATE);
        loadUserInfoFromStorage();
    }

    /**
     * Gets the singleton instance of the TuyaApiClient.
     *
     * @param context Application context.
     * @return The singleton instance.
     */
    public static SmartLife getInstance(Context context) {
        if (instance == null) {
            synchronized (SmartLife.class) {
                if (instance == null) {
                    instance = new SmartLife(context);
                }
            }
        }
        return instance;
    }

    private void loadUserInfoFromStorage() {
        String userInfoJson = storage.getString("userInfo", null);
        if (userInfoJson != null) {
            this.userInfo = gson.fromJson(userInfoJson, UserInfo.class);
            Log.d(TAG, "User info loaded from storage.");
        } else {
            this.userInfo = new UserInfo();
            Log.d(TAG, "No saved user info found, created a new one.");
        }
    }


    // =================================================================================
    // Public API Methods (Matching the JavaScript File)
    // =================================================================================

    /**
     * Provides a copy of the current user info state.
     */
    public UserInfo getUserInfo() {
        // Return a copy to prevent external modification
        return gson.fromJson(gson.toJson(this.userInfo), UserInfo.class);
    }

    /**
     * Authenticates the user and retrieves access tokens.
     */
    public void login(String username, String password, String region, @NonNull SmartLifeCallback<Boolean> callback) {
        executor.execute(() -> {
            // First, load from storage to ensure we have the latest persisted state
            loadUserInfoFromStorage();

            if (userInfo.isTokenValid()) {
                Log.d(TAG, "Token is still valid, login skipped.");
                postSuccess(callback, true);
                return;
            }

            if (!REGION_BASE_URL.containsKey(region)) {
                postFailure(callback, new IllegalArgumentException("Invalid region: " + region));
                return;
            }

            // Set the base URL for the selected region
            userInfo.baseUrl = REGION_BASE_URL.get(region);

            String url = userInfo.baseUrl + "/homeassistant/auth.do";
            FormBody formData = new FormBody.Builder()
                    .add("userName", username)
                    .add("password", password)
                    .add("countryCode", Objects.requireNonNull(REGION_VALUES.get(region)))
                    .add("bizType", "smart_life")
                    .add("from", "tuya")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formData)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            makeRequest(request, AuthResponse.class, new SmartLifeCallback<AuthResponse>() {
                @Override
                public void onSuccess(AuthResponse response) {
                    storeTokens(response);
                    Log.i(TAG, "Login successful.");
                    postSuccess(callback, true);
                }

                @Override
                public void onFailure(Exception e) {
                    userInfo.loggedIn = false;
                    Log.e(TAG, "Login failed.", e);
                    postFailure(callback, e);
                }
            });
        });
    }

    /**
     * Refreshes the access token using the stored refresh token.
     */
    public void refreshToken(@NonNull SmartLifeCallback<Boolean> callback) {
        executor.execute(() -> {
            if (userInfo.refreshToken == null || userInfo.refreshToken.isEmpty()) {
                postFailure(callback, new IllegalStateException("No refresh token available. Please login first."));
                return;
            }

            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(userInfo.baseUrl + "/homeassistant/access.do"))
                    .newBuilder()
                    .addQueryParameter("grant_type", "refresh_token")
                    .addQueryParameter("refresh_token", userInfo.refreshToken)
                    .addQueryParameter("rand", String.valueOf(Math.random()))
                    .build();

            Request request = new Request.Builder().url(url).get().build();

            makeRequest(request, AuthResponse.class, new SmartLifeCallback<AuthResponse>() {
                @Override
                public void onSuccess(AuthResponse response) {
                    storeTokens(response);
                    Log.i(TAG, "Token refresh successful.");
                    postSuccess(callback, true);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Token refresh failed.", e);
                    postFailure(callback, e);
                }
            });
        });
    }

    /**
     * Fetches the list of devices associated with the user's account.
     */
    public void getDeviceList(@NonNull SmartLifeCallback<List<Device>> callback) {
        checkToken(new SmartLifeCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                SkillHeader header = new SkillHeader();
                header.name = "Discovery";
                header.namespace = "discovery";

                DiscoveryPayload payload = new DiscoveryPayload();
                payload.accessToken = userInfo.accessToken;

                SkillRequest<DiscoveryPayload> skillRequestData = new SkillRequest<>();
                skillRequestData.payload = payload;
                skillRequestData.header = header;

                String json = gson.toJson(skillRequestData);
                RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);
                Request request = new Request
                        .Builder()
                        .url(userInfo.baseUrl + "/homeassistant/skill")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                makeRequest(request, SkillResponse.class, new SmartLifeCallback<SkillResponse>() {
                    @Override
                    public void onSuccess(SkillResponse response) {if (response != null && response.payload != null && response.payload.devices != null) {
                            userInfo.devices = response.payload.devices;
                            persistUserInfo(); // Save updated device list
                            Log.i(TAG, "Device list fetched successfully: " + userInfo.devices.size() + " devices found.");
                            postSuccess(callback, userInfo.devices);
                        } else {
                            postFailure(callback, new Exception("Failed to parse device list from response."));
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        postFailure(callback, e);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                postFailure(callback, e);
            }
        });
    }

    /**
     * Sends a command to a device (e.g., turn on/off, change brightness).
     */
    public void adjustDevice(Device device, String action, String valueName, Object newState, @NonNull SmartLifeCallback<Boolean> callback) {
        checkToken(new SmartLifeCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                SkillHeader header = new SkillHeader();
                header.name = action;
                header.namespace = "control";

                // Use a Map for the payload to handle dynamic key-value pairs like in the JS code
                Map<String, Object> payloadMap = new HashMap<>();
                payloadMap.put("accessToken", userInfo.accessToken);
                payloadMap.put("devId", device.id);
                payloadMap.put(valueName, newState);

                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("header", header);
                requestMap.put("payload", payloadMap);

                RequestBody body = RequestBody.create(gson.toJson(requestMap), JSON_MEDIA_TYPE);
                Request request = new Request.Builder()
                        .url(userInfo.baseUrl + "/homeassistant/skill")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                makeRequest(request, SkillResponse.class, new SmartLifeCallback<SkillResponse>() {
                    @Override
                    public void onSuccess(SkillResponse response) {
                        Log.i(TAG, "Device '" + device.name + "' adjusted successfully. Action: " + action);
                        postSuccess(callback, true);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        postFailure(callback, e);
                    }
                });
            }
            @Override
            public void onFailure(Exception e) {
                postFailure(callback, e);
            }
        });
    }

    /**
     * Toggles the power state of a device.
     */
    public void toggleDevice(Device device, @NonNull SmartLifeCallback<Boolean> callback) {
        Object state = device.data != null ? device.data.get("state") : null;
        boolean currentStateOn = state instanceof Boolean && (Boolean) state;
        int newState = currentStateOn ? 0 : 1; // 1 for ON, 0 for OFF
        adjustDevice(device, "turnOnOff", "value", newState, callback);
    }

    /**
     * Turns a device ON or OFF.
     */
    public void turnDevice(Device device, boolean onOrOff, @NonNull SmartLifeCallback<Boolean> callback) {
        adjustDevice(device, "turnOnOff", "value", onOrOff ? 1 : 0, callback);
    }

    /**
     * Changes the brightness of a light.
     */
    public void changeBrightness(Device device, int newBrightness, @NonNull SmartLifeCallback<Boolean> callback) {
        adjustDevice(device, "brightnessSet", "value", newBrightness, callback);
    }

    /**
     * Changes the color temperature of a light.
     */
    public void changeColorTemperature(Device device, int newTemperature, @NonNull SmartLifeCallback<Boolean> callback) {
        adjustDevice(device, "colorTemperatureSet", "value", newTemperature, callback);
    }


    // =================================================================================
    // Private Helper Methods
    // =================================================================================

    /**
     * Generic helper for making network requests and parsing the response.
     */
    private <T> void makeRequest(Request request, Class<T> responseClass, @NonNull SmartLifeCallback<T> callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postFailure(callback, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("Request failed: " + response.code() + " " + response.message());
                    }
                    String jsonString = body.string();
                    T responseObject = gson.fromJson(jsonString, responseClass);
                    if (responseObject == null) {
                        throw new IOException("Failed to parse JSON response: " + jsonString);
                    }
                    postSuccess(callback, responseObject);
                } catch (Exception e) {
                    postFailure(callback, e);
                }
            }
        });
    }

    /**
     * Updates the local userInfo state with new tokens and persists it.
     */
    private void storeTokens(AuthResponse response) {
        userInfo.accessToken = response.accessToken;
        userInfo.refreshToken = response.refreshToken;
        userInfo.expiresIn = System.currentTimeMillis() + (response.expiresInSeconds * 1000);
        userInfo.loggedIn = true;
        persistUserInfo();
    }

    /**
     * Saves the current userInfo object to SharedPreferences.
     */
    private void persistUserInfo() {
        String userInfoJson = gson.toJson(userInfo);
        storage.edit().putString("userInfo", userInfoJson).apply();
    }

    /**
     * Ensures a valid token exists before making an API call, refreshing if necessary.
     */
    private void checkToken(@NonNull SmartLifeCallback<Boolean> callback) {
        executor.execute(() -> {
            if (userInfo.isTokenValid()) {
                postSuccess(callback, true);
                return;
            }

            Log.d(TAG, "Token expired or invalid, attempting to refresh...");
            refreshToken(new SmartLifeCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    postSuccess(callback, true); // Refresh succeeded
                }
                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Token refresh failed. User needs to log in again.", e);
                    postFailure(callback, new Exception("Authentication token expired. Please log in again."));
                }
            });
        });
    }

    /**
     * Helper to post a success callback on the main UI thread.
     */
    private <T> void postSuccess(@NonNull final SmartLifeCallback<T> callback, final T result) {
        mainThreadHandler.post(() -> {
            if(callback != null) callback.onSuccess(result);
        });
    }

    /**
     * Helper to post a failure callback on the main UI thread.
     */
    private <T> void postFailure(@NonNull final SmartLifeCallback<T> callback, @NonNull final Exception e) {
        mainThreadHandler.post(() -> {
            if(callback!= null) callback.onFailure(e);
        });
    }
}


/*
// =================================================================================
// EXAMPLE USAGE IN AN ANDROID ACTIVITY
// =================================================================================

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.yourcompany.yourapp.R; // Your R file
import com.yourcompany.yourapp.api.TuyaApiClient;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String DEMO_TAG = "TuyaDemo";

    // --- IMPORTANT: Use your actual credentials and region ---
    private final String TUYA_USERNAME = "your_smartlife_email@example.com";
    private final String TUYA_PASSWORD = "your_smartlife_password";
    private final String TUYA_REGION = "EU"; // Or "US", "CN"

    private TuyaApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the singleton instance of the client
        apiClient = TuyaApiClient.getInstance(getApplicationContext());

        Button loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(v -> executeLoginAndListDevices());
    }

    private void executeLoginAndListDevices() {
        showToast("Logging in...");
        apiClient.login(TUYA_USERNAME, TUYA_PASSWORD, TUYA_REGION, new TuyaApiClient.SmartLifeCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Log.i(DEMO_TAG, "Login successful!");
                showToast("Login Successful!");

                // Now that we're logged in, get the list of devices
                fetchDeviceList();
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(DEMO_TAG, "Login failed", e);
                showToast("Login Failed: " + e.getMessage());
            }
        });
    }

    private void fetchDeviceList() {
        showToast("Fetching devices...");
        apiClient.getDeviceList(new TuyaApiClient.SmartLifeCallback<List<TuyaApiClient.Device>>() {
            @Override
            public void onSuccess(List<TuyaApiClient.Device> devices) {
                Log.i(DEMO_TAG, "Found " + devices.size() + " devices.");
                showToast("Found " + devices.size() + " devices.");

                for (TuyaApiClient.Device device : devices) {
                    Log.d(DEMO_TAG, "Device: " + device.name + ", ID: " + device.id + ", Data: " + device.data);
                }

                // Example: Try to toggle the first device
                if (!devices.isEmpty()) {
                    toggleFirstDevice(devices.get(0));
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(DEMO_TAG, "Failed to get devices", e);
                showToast("Error getting devices: " + e.getMessage());
            }
        });
    }

    private void toggleFirstDevice(TuyaApiClient.Device device) {
        showToast("Toggling device: " + device.name);
        apiClient.toggleDevice(device, new TuyaApiClient.SmartLifeCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Log.i(DEMO_TAG, "Successfully toggled " + device.name);
                showToast("Toggled " + device.name + " successfully!");
                // Note: The local device state isn't updated here, you might want to re-fetch
                // the device list or update the local 'device' object manually for an immediate UI change.
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(DEMO_TAG, "Failed to toggle device", e);
                showToast("Failed to toggle " + device.name + ": " + e.getMessage());
            }
        });
    }

    private void showToast(String message) {
        // Ensure toast is shown on the main thread
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }
}
*/