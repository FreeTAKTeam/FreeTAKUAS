package org.FreeTak.FreeTAKUAS;

import org.FreeTak.FreeTAKUAS.R;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import org.FreeTak.FreeTAKUAS.BuildConfig;

/** Main activity that displays three choices to user */
public class MainActivity extends Activity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = "MainActivity";
    private static final String LAST_USED_FTS_IP = "ftsip";
    private static final String LAST_USED_FTS_API = "ftsapikey";
    private static final String LAST_USED_DRONE_NAME = "drone_name";
    private static final String LAST_USED_RTMP_IP = "rtmp_ip";
    private static final String LAST_USED_RTMP_HD = "rtmp_hd";
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static boolean isAppStarted = false;
    public int ready = 0;

    private final Handler button_handler = new Handler();
    private final Handler server_handler = new Handler();
    private Runnable button_runnable;
    private Runnable server_runnable;

    private DJISDKManager.SDKManagerCallback registrationCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onRegister(DJIError error) {
            isRegistrationInProgress.set(false);
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                //loginAccount();
                DJISDKManager.getInstance().startConnectionToProduct();
                Toast.makeText(getApplicationContext(), "SDK registration succeeded!", Toast.LENGTH_SHORT).show();
            } else {

                Toast.makeText(getApplicationContext(),
                        "Registration failed: " + error.getDescription(),
                        Toast.LENGTH_LONG).show();
            }
        }
        @Override
        public void onProductDisconnect() {
            Toast.makeText(getApplicationContext(),
                    "UAS and Controller disconnect!",
                    Toast.LENGTH_LONG).show();
            ready = ready ^ 32;
        }

        @Override
        public void onProductConnect(BaseProduct product) {
            Toast.makeText(getApplicationContext(),
                    "UAS and Controller connected!",
                    Toast.LENGTH_SHORT).show();
            ready = ready | 32;
        }

        @Override
        public void onProductChanged(BaseProduct product) {

        }

        @Override
        public void onComponentChange(BaseProduct.ComponentKey key,
                                      BaseComponent oldComponent,
                                      BaseComponent newComponent) {
            /*
            Toast.makeText(getApplicationContext(),
                           key.toString() + " changed",
                           Toast.LENGTH_LONG).show();
            */
        }

        @Override
        public void onInitProcess(DJISDKInitEvent event, int totalProcess) {

        }

        @Override
        public void onDatabaseDownloadProgress(long current, long total) {

        }
    };

    private void loginAccount(){
        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Toast.makeText(getApplicationContext(),
                                "Login Success!",
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        Toast.makeText(getApplicationContext(),
                                "Login Error!",
                                Toast.LENGTH_LONG).show();
                    }
                });

    }

    public static boolean isStarted() {
        return isAppStarted;
    }
    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
            Manifest.permission.VIBRATE, // Gimbal rotation
            Manifest.permission.INTERNET, // API requests
            Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
            Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
            Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
            Manifest.permission.ACCESS_FINE_LOCATION, // Maps
            Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
            Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
            Manifest.permission.BLUETOOTH, // Bluetooth connected products
            Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
            Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
            Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
            Manifest.permission.RECORD_AUDIO // Speaker accessory
    };
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private List<String> missingPermission = new ArrayList<>();
    private EditText FtsIpEditText, FtsApiEditText, DroneNameEditText, RtmpIpEditText;
    private CheckBox RtmpHDEnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isAppStarted = true;
        findViewById(R.id.complete_ui_widgets).setOnClickListener(this);
        ((Button) findViewById(R.id.complete_ui_widgets)).setText(R.string.uas_button_disabled);
        //findViewById(R.id.bt_customized_ui_widgets).setOnClickListener(this);
        //findViewById(R.id.bt_map_widget).setOnClickListener(this);
        TextView versionText = (TextView) findViewById(R.id.app_version);
        versionText.setText(R.string.app_version);

        RtmpHDEnable = (CheckBox) findViewById(R.id.hd_stream);
        RtmpHDEnable.setChecked(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean(LAST_USED_RTMP_HD, false));
        RtmpHDEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean(LAST_USED_RTMP_HD, true).apply();
                } else {
                    PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean(LAST_USED_RTMP_HD, false).apply();
                }
            }
        });

        FtsIpEditText = (EditText) findViewById(R.id.edittext_fts_ip);
        FtsIpEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_FTS_IP, BuildConfig.FTSIP));
        FtsIpEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event != null && event.isShiftPressed()) {
                        return false;
                    } else {
                        // the user is done typing.
                        handleFtsIPTextChange();
                    }
                }
                return false; // pass on to other listeners.
            }
        });
        FtsIpEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line characcter
                    final String currentText = FtsIpEditText.getText().toString();
                    FtsIpEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleFtsIPTextChange();
                }
            }
        });
        FtsApiEditText = (EditText) findViewById(R.id.edittext_fts_apikey);
        FtsApiEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_FTS_API, BuildConfig.FTSAPIKEY));
        FtsApiEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event != null && event.isShiftPressed()) {
                        return false;
                    } else {
                        // the user is done typing.
                        handleFtsApiKeyTextChange();
                    }
                }
                return false; // pass on to other listeners.
            }
        });
        FtsApiEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line characcter
                    final String currentText = FtsApiEditText.getText().toString();
                    FtsApiEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleFtsApiKeyTextChange();
                }
            }
        });
        RtmpIpEditText = (EditText) findViewById(R.id.edittext_rtmp_ip);
        RtmpIpEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_RTMP_IP, BuildConfig.RTMPIP));
        RtmpIpEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event != null && event.isShiftPressed()) {
                        return false;
                    } else {
                        // the user is done typing.
                        handleRtmpIpTextChange();
                    }
                }
                return false; // pass on to other listeners.
            }
        });
        RtmpIpEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line characcter
                    final String currentText = RtmpIpEditText.getText().toString();
                    RtmpIpEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleRtmpIpTextChange();
                }
            }
        });
        DroneNameEditText = (EditText) findViewById(R.id.edittext_drone_name);
        DroneNameEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_DRONE_NAME, BuildConfig.DRONENAME));
        DroneNameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event != null && event.isShiftPressed()) {
                        return false;
                    } else {
                        // the user is done typing.
                        handleDroneNameTextChange();
                    }
                }
                return false; // pass on to other listeners.
            }
        });
        DroneNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().contains("\n")) {
                    // the user is done typing.
                    // remove new line characcter
                    final String currentText = DroneNameEditText.getText().toString();
                    DroneNameEditText.setText(currentText.substring(0, currentText.indexOf('\n')));
                    handleDroneNameTextChange();
                }
            }
        });
        checkAndRequestPermissions();
        handleFtsIPTextChange();
        handleFtsApiKeyTextChange();
        handleRtmpIpTextChange();
        handleDroneNameTextChange();

        button_handler.postDelayed(button_runnable = () -> {
            button_handler.postDelayed(button_runnable, 500);
            enable_controller_button();
        }, 500);

        server_handler.postDelayed(server_runnable = () -> {
            server_handler.postDelayed(server_runnable, 500);
            if ((ready & 32) == 32) {
                server_handler.removeCallbacks(server_runnable);
                return;
            }
            Thread apicheck = new Thread(() -> server_version_supported());
            if (!apicheck.isAlive() && ((ready & 3) == 3)) // make sure ftsIp and ftsApikey are set
                try {
                    apicheck.start();
                } catch(IllegalThreadStateException e) {
                    Log.i(TAG, String.format("Thread error: %s",e));
                }
            apicheck = null;
        }, 500);

    }

    @Override
    protected void onDestroy() {
        button_handler.removeCallbacks(button_runnable);
        server_handler.removeCallbacks(server_runnable);
        DJISDKManager.getInstance().destroy();
        ready = 0;
        isAppStarted = false;
        super.onDestroy();
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions! Will not register SDK to connect to aircraft.", Toast.LENGTH_LONG).show();
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    DJISDKManager.getInstance().registerApp(MainActivity.this, registrationCallback);
                }
            });
        }
    }

    @Override
    public void onClick(View view) {
        Class nextActivityClass = null;

        int id = view.getId();
        if (id == R.id.complete_ui_widgets) {
            nextActivityClass = CompleteWidgetActivity.class;

            if (!enable_controller_button()) {
                if ((ready & 16) == 0 && (ready & 3) == 3) {
                    Toast.makeText(getApplicationContext(), "Your configured FTS does not appear to support the UAS client", Toast.LENGTH_SHORT).show();
                    return;
                } else if (ready < 0x20) {
                    Toast.makeText(getApplicationContext(), "No controller detected and/or configuration is missing!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            server_handler.removeCallbacks(server_runnable);
            button_handler.removeCallbacks(button_runnable);
            Intent intent = new Intent(this, nextActivityClass);
            startActivity(intent);
        }
        /*
        else if (id == R.id.bt_customized_ui_widgets) {
            nextActivityClass = CustomizedWidgetsActivity.class;
        } else {
            nextActivityClass = MapWidgetActivity.class;
            PopupMenu popup = new PopupMenu(this, view);
            popup.setOnMenuItemClickListener(this);
            Menu popupMenu = popup.getMenu();
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.map_select_menu, popupMenu);
            popupMenu.findItem(R.id.here_map).setEnabled(isHereMapsSupported());
            popupMenu.findItem(R.id.google_map).setEnabled(isGoogleMapsSupported(this));
            popup.show();
            return;
        }
        */
        Intent intent = new Intent(this, nextActivityClass);
        startActivity(intent);

    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {

        Intent intent = new Intent(this, MapWidgetActivity.class);
        int mapBrand = 0;
        switch (menuItem.getItemId()) {
            case R.id.here_map:
                mapBrand = 0;
                break;
/*
            case R.id.google_map:
                mapBrand = 1;
                break;
            case R.id.amap:
                mapBrand = 2;
                break;
            case R.id.mapbox:
                mapBrand = 3;
                break;
 */
        }
        intent.putExtra(MapWidgetActivity.MAP_PROVIDER, mapBrand);
        startActivity(intent);

        return false;
    }

    public static boolean isHereMapsSupported() {
        String abi;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.CPU_ABI;
        } else {
            abi = Build.SUPPORTED_ABIS[0];
        }
        DJILog.d(TAG, "abi=" + abi);

        //The possible values are armeabi, armeabi-v7a, arm64-v8a, x86, x86_64, mips, mips64.
        return abi.contains("arm");
    }

    public static boolean isGoogleMapsSupported(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public void server_version_supported() {

        String apiversion;
        String ftsip = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsip","");
        String ftsapikey = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsapikey","");

        if (ftsip.isEmpty() || ftsapikey.isEmpty())
            return;

        try {
            URL url = new URL("http://" + ftsip + "/manageAPI/getHelp");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Authorization", ftsapikey);

            InputStream inputStream;
            // get stream
            if (urlConnection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                inputStream = urlConnection.getInputStream();
            } else {
                inputStream = urlConnection.getErrorStream();
            }
            // parse stream
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String temp, response = "";
            while ((temp = bufferedReader.readLine()) != null) {
                response += temp;
            }
            // put into JSONObject
            JSONObject ResponseObject = new JSONObject();
            ResponseObject.put("Content", response);
            ResponseObject.put("Code", urlConnection.getResponseCode());
            ResponseObject.put("Message", urlConnection.getResponseMessage());
            ResponseObject.put("Length", urlConnection.getContentLength());
            ResponseObject.put("Type", urlConnection.getContentType());
            urlConnection.disconnect();

            try {
                JSONObject content = new JSONObject(ResponseObject.get("Content").toString());
                 apiversion = content.get("APIVersion").toString();
                if (Float.parseFloat(apiversion) < 1.9f) {
                    Log.i(TAG, String.format("FTS version %s does not support FreeTakUAS",apiversion));
                    return;
                }
            } catch (JSONException e) {
                Log.i(TAG, "Didn't receive JSON from the server");
                return;
            }
        } catch (Exception e) {
            Log.i(TAG, String.format("Problem validating the FTS api version: %s", e.toString()));
            return;
        }
        Log.i(TAG, String.format("FTS version %s API check success",apiversion));
        ready = ready | 16;
    }

    private boolean enable_controller_button() {

        if (ready == 0x3f) {
            ((Button) findViewById(R.id.complete_ui_widgets)).setText(R.string.uas_button_enabled);
            findViewById(R.id.complete_ui_widgets).setEnabled(true);
            button_handler.removeCallbacks(button_runnable);
            return true;
        }
        ((Button) findViewById(R.id.complete_ui_widgets)).setText(R.string.uas_button_disabled);
        return false;
    }

    private void handleFtsIPTextChange() {
        // the user is done typing.
        final String FtsIP = FtsIpEditText.getText().toString();

        if (!TextUtils.isEmpty(FtsIP)) {
            if (!FtsIP.contains(":")) {
                Toast.makeText(getApplicationContext(),"ERROR: Bad IP:PORT",Toast.LENGTH_SHORT).show();
                FtsIpEditText.setText("");
                return;
            }
//            Toast.makeText(getApplicationContext(),"FTS IP:PORT = " + FtsIP,Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_FTS_IP,FtsIP).apply();
            ready = ready | 1;
            enable_controller_button();
        }
    }

    private void handleFtsApiKeyTextChange() {
        // the user is done typing.
        final String FtsAPIKey = FtsApiEditText.getText().toString();

        if (!TextUtils.isEmpty(FtsAPIKey)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_FTS_API,FtsAPIKey).apply();
            ready = ready | 2;
            enable_controller_button();
        }
    }

    private void handleRtmpIpTextChange() {
        // the user is done typing.
        final String rtmp_ip = RtmpIpEditText.getText().toString();

        if (!TextUtils.isEmpty(rtmp_ip)) {
            if (!rtmp_ip.contains(":")) {
                Toast.makeText(getApplicationContext(),"ERROR: Bad IP:PORT",Toast.LENGTH_SHORT).show();
                RtmpIpEditText.setText("");
                return;
            }
//            Toast.makeText(getApplicationContext(),"RTMP IP:PORT = " + rtmp_ip,Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_RTMP_IP,rtmp_ip).apply();
            ready = ready | 4;
            enable_controller_button();
        }
    }

    private void handleDroneNameTextChange() {
        // the user is done typing.
        final String drone_name = DroneNameEditText.getText().toString();

        if (!TextUtils.isEmpty(drone_name)) {
//            Toast.makeText(getApplicationContext(),"Drone Name: " + drone_name,Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_DRONE_NAME,drone_name).apply();
            ready = ready | 8;
            enable_controller_button();
        }
    }
}
