package org.FreeTak.FreeTAKUAS;

import org.FreeTak.FreeTAKUAS.R;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

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

/** Main activity that displays three choices to user */
public class MainActivity extends Activity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = "MainActivity";
    private static final String LAST_USED_FTS_IP = "ftsip";
    private static final String LAST_USED_FTS_API = "ftsapikey";
    private static final String LAST_USED_DRONE_NAME = "drone_name";
    private static final String LAST_USED_RTMP_IP = "rtmp_ip";
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static boolean isAppStarted = false;
    private int ready = 0;

    private DJISDKManager.SDKManagerCallback registrationCallback = new DJISDKManager.SDKManagerCallback() {


        @Override
        public void onRegister(DJIError error) {
            isRegistrationInProgress.set(false);
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                //loginAccount();
                DJISDKManager.getInstance().startConnectionToProduct();
                Toast.makeText(getApplicationContext(), "SDK registration succeeded!", Toast.LENGTH_LONG).show();
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
            ready = ready ^ 16;
        }

        @Override
        public void onProductConnect(BaseProduct product) {
            Toast.makeText(getApplicationContext(),
                           "UAS and Controller connected!",
                           Toast.LENGTH_LONG).show();
            ready = ready | 16;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isAppStarted = true;
        findViewById(R.id.complete_ui_widgets).setOnClickListener(this);
        ((Button) findViewById(R.id.complete_ui_widgets)).setText("UAS [NOT READY]");
        findViewById(R.id.bt_customized_ui_widgets).setOnClickListener(this);
        findViewById(R.id.bt_map_widget).setOnClickListener(this);
        TextView versionText = (TextView) findViewById(R.id.app_version);
        versionText.setText(R.string.app_version);
        FtsIpEditText = (EditText) findViewById(R.id.edittext_fts_ip);
        FtsIpEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_FTS_IP,"204.48.30.216:19023"));
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
        FtsApiEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_FTS_API,"OrionLab11"));
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
        RtmpIpEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_RTMP_IP,"64.227.70.49:1935"));
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
        DroneNameEditText.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(LAST_USED_DRONE_NAME,"djcombo"));
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
    }

    @Override
    protected void onDestroy() {
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
        Class nextActivityClass;

        int id = view.getId();
        if (id == R.id.complete_ui_widgets) {
            nextActivityClass = CompleteWidgetActivity.class;
            if (!enable_controller_button()) {
                Toast.makeText(getApplicationContext(), "No controller detected and/or configuration is missing!", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (id == R.id.bt_customized_ui_widgets) {
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
            case R.id.google_map:
                mapBrand = 1;
                break;
            case R.id.amap:
                mapBrand = 2;
                break;
            case R.id.mapbox:
                mapBrand = 3;
                break;
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

    private boolean enable_controller_button() {
        if (ready == 0x1f) {
            ((Button) findViewById(R.id.complete_ui_widgets)).setText("UAS [READY]");
            findViewById(R.id.complete_ui_widgets).setEnabled(true);
            return true;
        }
        ((Button) findViewById(R.id.complete_ui_widgets)).setText("UAS [NOT READY]");
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
