package org.FreeTak.FreeTAKUAS;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.dji.mapkit.core.maps.DJIMap;
import com.dji.mapkit.core.models.DJILatLng;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Random;

import dji.common.airlink.PhysicalSource;
import dji.common.flightcontroller.CompassState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.gimbal.Attitude;
import dji.common.gimbal.GimbalState;
import dji.common.model.LocationCoordinate2D;
import dji.keysdk.CameraKey;
import dji.keysdk.KeyManager;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.MapWidget;
import dji.ux.widget.controls.CameraControlsWidget;

/**
 * Activity that shows all the UI elements together
 */
public class CompleteWidgetActivity extends Activity {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private FPVWidget fpvWidget;
    private FPVWidget secondaryFPVWidget;
    private RelativeLayout primaryVideoView;
    private FrameLayout secondaryVideoView;
    private boolean isMapMini = true;

    private String TAG = this.getClass().getName();

    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;

    private LiveStreamManager l;
    private FlightController mFlightController = null;
    private Gimbal mGimbal = null;


    // handlers will run the AsyncTask to talk to the FST REST API
    private final Handler sensor_handler = new Handler();
    private final Handler stream_handler = new Handler();
    private Runnable sensor_runnable, stream_runnable;
    // every 3 seconds
    int delay = 3000;

    public String FTS_IP, FTS_APIKEY, FTS_GUID, drone_name, rtmp_ip;
    public double droneLocationLat, droneLocationLng, droneLocationAlt, droneDistance, droneHeading;
    public double homeLocationLat, homeLocationLng;
    public float gimbalPitch, gimbalRoll, gimbalYaw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_widgets);

        height = DensityUtil.dip2px(this, 100);
        width = DensityUtil.dip2px(this, 150);
        margin = DensityUtil.dip2px(this, 12);

        FTS_IP = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsip","204.48.30.216:8087");
        FTS_APIKEY = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsapikey","OrionLab11");
        drone_name = PreferenceManager.getDefaultSharedPreferences(this).getString("drone_name","uas_dji");
        rtmp_ip = PreferenceManager.getDefaultSharedPreferences(this).getString("rtmp_ip","172.30.254.237:1935");

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        deviceHeight = outPoint.y;
        deviceWidth = outPoint.x;

        mapWidget = findViewById(R.id.map_widget);
        mapWidget.initAMap(new MapWidget.OnMapReadyListener() {
            @Override
            public void onMapReady(@NonNull DJIMap map) {
                map.setOnMapClickListener(new DJIMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(DJILatLng latLng) {
                        onViewClick(mapWidget);
                    }
                });
                map.getUiSettings().setZoomControlsEnabled(false);
            }
        });
        mapWidget.onCreate(savedInstanceState);

        parentView = (ViewGroup) findViewById(R.id.root_view);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onViewClick(fpvWidget);
            }
        });

        primaryVideoView = (RelativeLayout) findViewById(R.id.fpv_container);
        secondaryVideoView = (FrameLayout) findViewById(R.id.secondary_video_view);
        secondaryFPVWidget = findViewById(R.id.secondary_fpv_widget);
        secondaryFPVWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                swapVideoSource();
            }
        });
        if (VideoFeeder.getInstance() != null) {
            //If secondary video feed is already initialized, get video source
            updateSecondaryVideoVisibility(VideoFeeder.getInstance().getSecondaryVideoFeed().getVideoSource() != PhysicalSource.UNKNOWN);
            //If secondary video feed is not yet initialized, wait for active status
            VideoFeeder.getInstance().getSecondaryVideoFeed()
                    .addVideoActiveStatusListener(isActive ->
                            runOnUiThread(() -> updateSecondaryVideoVisibility(isActive)));
        }
    }

    private void onViewClick(View view) {
        if (view == fpvWidget && !isMapMini) {
            resizeFPVWidget(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT, 0, 0);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, deviceWidth, deviceHeight, width, height, margin);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = true;
        } else if (view == mapWidget && isMapMini) {
            hidePanels();
            resizeFPVWidget(width, height, margin, 12);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
        }
    }

    private void resizeFPVWidget(int width, int height, int margin, int fpvInsertPosition) {
        RelativeLayout.LayoutParams fpvParams = (RelativeLayout.LayoutParams) primaryVideoView.getLayoutParams();
        fpvParams.height = height;
        fpvParams.width = width;
        fpvParams.rightMargin = margin;
        fpvParams.bottomMargin = margin;
        if (isMapMini) {
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        } else {
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        }
        primaryVideoView.setLayoutParams(fpvParams);

        parentView.removeView(primaryVideoView);
        parentView.addView(primaryVideoView, fpvInsertPosition);
    }

    private void reorderCameraCapturePanel() {
        View cameraCapturePanel = findViewById(R.id.CameraCapturePanel);
        parentView.removeView(cameraCapturePanel);
        parentView.addView(cameraCapturePanel, isMapMini ? 9 : 13);
    }

    private void swapVideoSource() {
        if (secondaryFPVWidget.getVideoSource() == FPVWidget.VideoSource.SECONDARY) {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
        } else {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
        }
    }

    private void updateSecondaryVideoVisibility(boolean isActive) {
        if (isActive) {
            secondaryVideoView.setVisibility(View.VISIBLE);
        } else {
            secondaryVideoView.setVisibility(View.GONE);
        }
    }

    private void hidePanels() {
        //These panels appear based on keys from the drone itself.
        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.HISTOGRAM_ENABLED), false, null);
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.COLOR_WAVEFORM_ENABLED), false, null);
        }

        //These panels have buttons that toggle them, so call the methods to make sure the button state is correct.
        CameraControlsWidget controlsWidget = findViewById(R.id.CameraCapturePanel);
        controlsWidget.setAdvancedPanelVisibility(false);
        controlsWidget.setExposurePanelVisibility(false);

        //These panels don't have a button state, so we can just hide them.
        findViewById(R.id.pre_flight_check_list).setVisibility(View.GONE);
        findViewById(R.id.rtk_panel).setVisibility(View.GONE);
        findViewById(R.id.spotlight_panel).setVisibility(View.GONE);
        findViewById(R.id.speaker_panel).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        boolean controller_status = initFlightController();
        boolean gimbal_status = initGimbal();
        if (controller_status) {
            if (gimbal_status) {
                // send sensor CoTs while this activity is open and the drone has real gps data
                sensor_handler.postDelayed(sensor_runnable = new Runnable() {
                    public void run() {
                        sensor_handler.postDelayed(sensor_runnable, delay);
                        if (checkGpsCoordinates(droneLocationLat, droneLocationLng) && FTS_IP != null && FTS_APIKEY != null && drone_name != null) {
                            Log.i(TAG, String.format("Updating drone position: alt %f long %f lat %f distance %f heading %f", droneLocationAlt, droneLocationLng, droneLocationLat, droneDistance, droneHeading));
                            Log.i(TAG, String.format("gimbal stats: pitch: %f roll %f yaw %f", gimbalPitch, gimbalRoll, gimbalYaw));
                            Toast.makeText(getApplicationContext(), "Sending UAS Location CoT", Toast.LENGTH_SHORT).show();
                            new SendCotTask(CompleteWidgetActivity.this).execute("sensor", FTS_IP, FTS_APIKEY, drone_name, droneLocationAlt, droneLocationLat, droneLocationLng, droneDistance, droneHeading);
                        } else {
                            // inform user what went wrong
                            if (FTS_IP == null)
                                Toast.makeText(getApplicationContext(), "ERROR: FTS IP:PORT not set!", Toast.LENGTH_LONG).show();
                            if (FTS_APIKEY == null)
                                Toast.makeText(getApplicationContext(), "ERROR: FTS API Key not set!", Toast.LENGTH_LONG).show();
                            if (rtmp_ip == null)
                                Toast.makeText(getApplicationContext(), "ERROR: RTMP IP:PORT not set!", Toast.LENGTH_LONG).show();
                            if (drone_name == null)
                                Toast.makeText(getApplicationContext(), "ERROR: UAS Identifier not set!", Toast.LENGTH_LONG).show();

                            if (!checkGpsCoordinates(droneLocationLat, droneLocationLng))
                                Toast.makeText(getApplicationContext(), "ERROR: GPS not valid", Toast.LENGTH_LONG).show();

                            // stop the thread
                            sensor_handler.removeCallbacks(sensor_runnable);
                        }
                    }
                }, delay);

                // send stream CoTs while this activity is open and the drone is streaming fpv
                stream_handler.postDelayed(stream_runnable = new Runnable() {
                    public void run() {
                        stream_handler.postDelayed(stream_runnable, delay);
                        String rtmp_path = "/LiveUAS/" + drone_name /*+ new Random().nextInt(9999)*/;
                        String live_url = "rtmp://" + rtmp_ip + rtmp_path;
                        //Toast.makeText(getApplicationContext(), String.format("RTMP URL: %s", live_url), Toast.LENGTH_SHORT).show();
                        l = DJISDKManager.getInstance().getLiveStreamManager();
                        if (!l.isStreaming()) {
                            l.registerListener((x) -> {
                                Log.d(TAG, "LiveStream callback:" + x);
                            });
                            l.setAudioMuted(true);
                            l.setVideoSource(LiveStreamManager.LiveStreamVideoSource.Primary);
                            l.setVideoEncodingEnabled(true);
                            l.setLiveUrl(live_url);

                            int rc = 0;
                            rc = l.startStream();
                            if (rc != 0) {
                                l.stopStream();
                                l.unregisterListener((x) -> {
                                    Log.d(TAG, "LiveStream callback:" + x);
                                });
                                // 254 probably means the user put the wrong IP:Port in
                                if (rc == 254) {
                                    Toast.makeText(getApplicationContext(), String.format("ERROR: RTMP Server error: %d\nCheck your RTMP configuration", rc), Toast.LENGTH_LONG).show();
                                    // kill this thread, the IP:Port are bad
                                    stream_handler.removeCallbacks(stream_runnable);
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), "RTMP Stream Established Successfully", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), "UAS streaming is active\nSending Sensor CoT", Toast.LENGTH_LONG).show();
                            new SendCotTask(CompleteWidgetActivity.this).execute("stream", FTS_IP, FTS_APIKEY, drone_name, rtmp_ip, rtmp_path);

                            // once the stream is up, stop sending the stream CoT
                            stream_handler.removeCallbacks(stream_runnable);
                        }
                    }
                }, delay);
            }
            else {
                Toast.makeText(getApplicationContext(), "ERROR: Gimbal did not init correctly\nCheck UAS is powered on", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Toast.makeText(getApplicationContext(), "ERROR: Flight Controller did not init\nCheck USB connection to controller", Toast.LENGTH_LONG).show();
        }

        mapWidget.onResume();
    }

    private boolean isFlightControllerSupported() {
        return DJISDKManager.getInstance().getProduct() != null &&
                DJISDKManager.getInstance().getProduct() instanceof Aircraft &&
                ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController() != null;
    }

    public static boolean checkGpsCoordinates(double latitude, double longitude) {
        //return true;
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private boolean initFlightController() {
        if (isFlightControllerSupported()) {
            mFlightController = ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController();
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(FlightControllerState
                                             djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    droneLocationAlt = djiFlightControllerCurrentState.getAircraftLocation().getAltitude();
                    droneHeading = mFlightController.getCompass().getHeading();

                    // compute the drone's distance from the home location, if it has one
                    if (djiFlightControllerCurrentState.isHomeLocationSet()) {
                        LocationCoordinate2D locationCoordinate2D = djiFlightControllerCurrentState.getHomeLocation();
                        homeLocationLat = locationCoordinate2D.getLatitude();
                        homeLocationLng = locationCoordinate2D.getLongitude();
                        droneDistance = distance(droneLocationLat, homeLocationLat, droneLocationLng, homeLocationLng, droneLocationAlt, 0);
                    }
                }
            });
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    public static double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

    private boolean isGimbalSupported() {
        return DJISDKManager.getInstance().getProduct() != null &&
                DJISDKManager.getInstance().getProduct() instanceof Aircraft &&
                ((Aircraft) DJISDKManager.getInstance().getProduct()).getGimbal() != null;
    }

    private boolean initGimbal() {
        if (isGimbalSupported()) {
            mGimbal = DJISDKManager.getInstance().getProduct().getGimbal();
            mGimbal.setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull @NotNull GimbalState gimbalCurrentState) {
                    Attitude attitude = gimbalCurrentState.getAttitudeInDegrees();
                    gimbalPitch = attitude.getPitch();
                    gimbalRoll = attitude.getRoll();
                    gimbalYaw = attitude.getYaw();
                }
            });
            return true;
        }
        else {
            return false;
        }
    }

    public void setDroneGUID(String uid) {
        FTS_GUID = uid;
    }

    public String getDroneGUID() {
        return FTS_GUID;
    }

    @Override
    protected void onPause() {
        // stop the sensor cot generation
        Log.i(TAG, "Stopping sensor thread");
        Toast.makeText(getApplicationContext(), "Stopping UAS Location CoT", Toast.LENGTH_LONG).show();
        sensor_handler.removeCallbacks(sensor_runnable);
        // stop the stream
        if (l.isStreaming()) {
            Log.i(TAG, "Stopping stream thread");
            Toast.makeText(getApplicationContext(), "Stopping Stream CoT", Toast.LENGTH_LONG).show();
            l.stopStream();
            stream_handler.removeCallbacks(stream_runnable);
        }

        mapWidget.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // stop the sensor cot generation
        Log.i(TAG, "Stopping sensor thread");
        sensor_handler.removeCallbacks(sensor_runnable);
        // stop the stream
        if (l.isStreaming()) {
            Log.i(TAG, "Stopping stream thread");
            l.stopStream();
            stream_handler.removeCallbacks(stream_runnable);
        }
        mapWidget.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapWidget.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapWidget.onLowMemory();
    }

    private class ResizeAnimation extends Animation {

        private View mView;
        private int mToHeight;
        private int mFromHeight;

        private int mToWidth;
        private int mFromWidth;
        private int mMargin;

        private ResizeAnimation(View v, int fromWidth, int fromHeight, int toWidth, int toHeight, int margin) {
            mToHeight = toHeight;
            mToWidth = toWidth;
            mFromHeight = fromHeight;
            mFromWidth = fromWidth;
            mView = v;
            mMargin = margin;
            setDuration(300);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
            float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) mView.getLayoutParams();
            p.height = (int) height;
            p.width = (int) width;
            p.rightMargin = mMargin;
            p.bottomMargin = mMargin;
            mView.requestLayout();
        }
    }
}
