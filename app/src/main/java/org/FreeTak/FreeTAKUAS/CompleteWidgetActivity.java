package org.FreeTak.FreeTAKUAS;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.FreeTak.FreeTAKUAS.customview.OverlayView;
import org.FreeTak.FreeTAKUAS.detector.Detector;
import org.FreeTak.FreeTAKUAS.tracking.MultiBoxTracker;
import org.jetbrains.annotations.NotNull;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.core.BaseVisionTaskApi;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.airlink.PhysicalSource;
import dji.common.flightcontroller.FlightControllerState;
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
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;
import dji.ux.widget.FPVOverlayWidget;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.MapWidget;
import dji.ux.widget.controls.CameraControlsWidget;

/**
 * Activity that shows all the UI elements together
 */
public class CompleteWidgetActivity extends Activity {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private ImageView crossHairView;
    private FPVWidget fpvWidget;
    private FPVWidget secondaryFPVWidget;
    private FPVOverlayWidget fpvOverlayWidget;
    private RelativeLayout primaryVideoView;
    private FrameLayout secondaryVideoView;
    private boolean isMapMini = true;

    private Button closePopupBtn, sendPopupBtn;
    private PopupWindow popupWindow;
    private NumberPicker attitudesNP,namesNP;

    private String TAG = this.getClass().getName();

    private String FTS_DRONE_UID, DRONE_SPI_UID;
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
    // send 4 sensor updates a second
    int sensor_delay = 500;
    // every 3 seconds try to send a stream cot, stop when we send one successfully
    int stream_delay = 3000;

    public String RTMP_URL = "";
    public boolean rtmp_hd, object_detect, od_75;
    public String FTS_IP, FTS_APIKEY, drone_name, rtmp_ip;
    public double droneLocationLat, droneLocationLng, droneDistance, droneHeading;
    public double homeLocationLat, homeLocationLng;
    public float droneLocationAlt, gimbalPitch, gimbalRoll, gimbalYaw, gimbalYawRelativeToAircraftHeading;

    public ToggleButton stream_toggle;
    public boolean stream_enabled = false;
    // this holds the geoobj names count
    private Dictionary names = new Hashtable();

    // ML stuff
    private final Handler objectDetector_handler = new Handler();
    private Runnable objectDetector_runnable;
    private ObjectDetector objectDetector = null;
    private OverlayView trackingOverlay = null;
    private MultiBoxTracker tracker;
    private long timestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_widgets);

        names.put("Alpha", 0);
        names.put("Bravo", 0);
        names.put("Charlie", 0);
        names.put("Delta", 0);
        names.put("Echo", 0);
        names.put("Foxtrot", 0);
        names.put("Golf", 0);
        names.put("Hotel", 0);
        names.put("India", 0);
        names.put("Juliett", 0);
        names.put("Kilo", 0);
        names.put("Lima", 0);
        names.put("Mike", 0);
        names.put("November", 0);
        names.put("Oscar", 0);
        names.put("Papa", 0);
        names.put("Quebec", 0);
        names.put("Romeo", 0);
        names.put("Sierra", 0);
        names.put("Tango", 0);
        names.put("Uniform", 0);
        names.put("Victor", 0);
        names.put("Whiskey", 0);
        names.put("X-ray", 0);
        names.put("Yankee", 0);
        names.put("Zulu", 0);

        height = DensityUtil.dip2px(this, 100);
        width = DensityUtil.dip2px(this, 150);
        margin = DensityUtil.dip2px(this, 12);

        FTS_IP = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsip", "");
        FTS_APIKEY = PreferenceManager.getDefaultSharedPreferences(this).getString("ftsapikey", "");
        drone_name = PreferenceManager.getDefaultSharedPreferences(this).getString("drone_name", "");
        rtmp_ip = PreferenceManager.getDefaultSharedPreferences(this).getString("rtmp_ip", "");
        rtmp_hd = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("rtmp_hd", false);
        object_detect = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("object_detect", false);
        od_75  = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("od_75", false);

        String rtmp_path = "/live/UAS-" + drone_name;
        RTMP_URL = "rtmp://" + rtmp_ip + rtmp_path;

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        deviceHeight = outPoint.y;
        deviceWidth = outPoint.x;

        mapWidget = findViewById(R.id.map_widget);
        mapWidget.initHereMap(map -> {
            map.setOnMapClickListener(latLng -> onViewClick(mapWidget));
            map.getUiSettings().setZoomControlsEnabled(false);
        });

        mapWidget.onCreate(savedInstanceState);

        parentView = (ViewGroup) findViewById(R.id.root_view);

        fpvOverlayWidget = findViewById(R.id.fpv_overlay_widget);
        fpvOverlayWidget.setSpotMeteringEnabled(false);
        fpvOverlayWidget.setTouchFocusEnabled(false);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(view -> onViewClick(fpvWidget));

        primaryVideoView = (RelativeLayout) findViewById(R.id.fpv_container);

        secondaryVideoView = (FrameLayout) findViewById(R.id.secondary_video_view);
        secondaryFPVWidget = findViewById(R.id.secondary_fpv_widget);
        secondaryFPVWidget.setOnClickListener(view -> swapVideoSource());

        if (VideoFeeder.getInstance() != null) {
            //If secondary video feed is already initialized, get video source
            updateSecondaryVideoVisibility(VideoFeeder.getInstance().getSecondaryVideoFeed().getVideoSource() != PhysicalSource.UNKNOWN);
            //If secondary video feed is not yet initialized, wait for active status
            VideoFeeder.getInstance().getSecondaryVideoFeed()
                    .addVideoActiveStatusListener(isActive ->
                            runOnUiThread(() -> updateSecondaryVideoVisibility(isActive)));
        }

        if (object_detect) {
            try {
                tracker = new MultiBoxTracker(this);
                trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
                trackingOverlay.addCallback(
                        canvas -> tracker.draw(canvas));

                AssetManager am = this.getApplicationContext().getAssets();
                //InputStream model = am.open("lite-model_object_detection_mobile_object_localizer_v1_1_metadata_2.tflite");
                InputStream model = am.open("lite-model_ssd_mobilenet_v1_1_metadata_2.tflite");
                ByteBuffer modelBytes = ByteBuffer.allocateDirect(model.available());
                while (model.available() > 0) {
                    modelBytes.put((byte) model.read());
                }
                model.close();

                List<String> allowedLabels = new ArrayList<>();
                allowedLabels.add("car");
                allowedLabels.add("truck");
                allowedLabels.add("airplane");
                allowedLabels.add("boat");
                allowedLabels.add("person");

                // Leave at least 2 cores open for the OS
                int numThreads = (Runtime.getRuntime().availableProcessors() * 2) - 2;

                // 42% score threshold default
                float thresholdScore = 0.42f;
                if (od_75)
                    thresholdScore = 0.75f;

                ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder().setNumThreads(numThreads).setLabelAllowList(allowedLabels).setScoreThreshold(thresholdScore).build();
                objectDetector = ObjectDetector.createFromBufferAndOptions(modelBytes, options);
                Toast.makeText(getApplicationContext(), "Loaded Tensorflow model",Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.i(TAG, String.format("Failed to load TFLite model: %s", e));
                Toast.makeText(getApplicationContext(), String.format("Failed to load Tensorflow model: %s", e),Toast.LENGTH_SHORT).show();
            }
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
        //Toast.makeText(getApplicationContext(), String.format("Model: %s", DJISDKManager.getInstance().getProduct().getModel()),Toast.LENGTH_LONG).show();
        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        l = DJISDKManager.getInstance().getLiveStreamManager();
        stream_toggle = findViewById(R.id.stream_toggle);
        stream_toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                stream_toggle.setChecked(true);
                stream_enabled = true;
                // send stream 1 stream CoT
                stream_handler.postDelayed(stream_runnable = () -> {
                    stream_handler.postDelayed(stream_runnable, stream_delay);
                    if (!l.isStreaming() && stream_enabled) {
                        Log.i(TAG,String.format("RTMP URL: %s", RTMP_URL));


                        //l.setLiveVideoBitRate(LiveVideoBitRateMode.AUTO.getValue());
                        l.setAudioStreamingEnabled(false);
                        l.setAudioMuted(true);
                        l.setVideoEncodingEnabled(true);
                        l.setLiveUrl(RTMP_URL);
                        l.setVideoSource(LiveStreamManager.LiveStreamVideoSource.Primary);

                        if (rtmp_hd) {
                            Log.i(TAG, "Streaming in 1080p");
                            //l.setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_1920_1080 );
                            //VideoFeeder.getInstance().setTranscodingDataRate(20.0f);
                        } else {
                            Log.i(TAG, "Streaming in 480p");
                            //l.setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_480_360 );
                            //VideoFeeder.getInstance().setTranscodingDataRate(0.3f);
                        }

                        int rc = 0;
                        rc = l.startStream();
                        if (rc != 0) {
                            l.stopStream();
                            // 254 probably means the user put the wrong IP:Port in
                            if (rc == 254) {
                                Toast.makeText(getApplicationContext(), "ERROR: Check your RTMP configuration\nStopping stream attempts", Toast.LENGTH_LONG).show();
                                // kill this thread, the IP:Port are bad
                                stream_toggle.setChecked(false);
                                stream_enabled = false;
                                stream_handler.removeCallbacks(stream_runnable);
                            }
                        } else if (l.isStreaming() && rc ==0){
                            new SendCotTask(CompleteWidgetActivity.this).execute("start_stream", FTS_IP, FTS_APIKEY, drone_name, rtmp_ip);
                            Toast.makeText(getApplicationContext(), "RTMP Stream Established Successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "RTMP Stream failed to start", Toast.LENGTH_SHORT).show();
                            stream_toggle.setChecked(false);
                            stream_enabled = false;
                        }
                    }
                }, stream_delay);
            } else {
                stream_toggle.setChecked(false);
                stream_enabled = false;
                stream_handler.removeCallbacks(stream_runnable);
                // stop the stream
                if (l.isStreaming()) {
                    Toast.makeText(getApplicationContext(), "Stopping RTMP Stream", Toast.LENGTH_SHORT).show();
                    l.stopStream();
                }
            }
        });

        crossHairView = findViewById(R.id.crosshair);
        crossHairView.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                LayoutInflater layoutInflater = (LayoutInflater) CompleteWidgetActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View geoObjView = layoutInflater.inflate(R.layout.geo_obj_popup, null);

                final String[] attitudes = new String[]{"unknown", "friendly", "hostile", "neutral"};
                final String[] pickedAttitude = new String[1];
                pickedAttitude[0] = attitudes[0];

                attitudesNP = geoObjView.findViewById(R.id.attitude);
                attitudesNP.setDisplayedValues(null);
                attitudesNP.setMinValue(0);
                attitudesNP.setMaxValue(attitudes.length - 1);
                //attitudesNP.setWrapSelectorWheel(false);
                attitudesNP.setDisplayedValues(attitudes);

                attitudesNP.setOnValueChangedListener((picker, oldVal, newVal) -> {
                    Log.d(TAG, String.format("GeoObj Attitude: %s", attitudes[newVal]));
                    pickedAttitude[0] = attitudes[newVal];
                });

                final String[] geoObjNames = new String[]{"Alpha","Bravo","Charlie","Delta","Echo","Foxtrot","Golf","Hotel","India","Juliett","Kilo","Lima","Mike","November","Oscar","Papa","Quebec","Romeo","Sierra","Tango","Uniform","Victor","Whiskey","X-ray","Yankee","Zulu"};
                final String[] pickedName = new String[1];
                final int[] howMany = {(int) names.get("Alpha")};
                pickedName[0] = geoObjNames[0];

                namesNP = geoObjView.findViewById(R.id.name);
                namesNP.setDisplayedValues(null);
                namesNP.setMinValue(0);
                namesNP.setMaxValue(geoObjNames.length - 1);
                //namesNP.setWrapSelectorWheel(false);
                namesNP.setDisplayedValues(geoObjNames);

                namesNP.setOnValueChangedListener((picker, oldVal, newVal) -> {
                    pickedName[0] = geoObjNames[newVal];
                    howMany[0] = (int) names.get(pickedName[0]);
                    Log.d(TAG, String.format("GeoObj Name: %s Count: %d", pickedName[0], howMany[0]));
                });

                closePopupBtn = (Button) geoObjView.findViewById(R.id.closePopupBtn);
                sendPopupBtn = (Button) geoObjView.findViewById(R.id.sendPopupBtn);

                popupWindow = new PopupWindow(geoObjView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                popupWindow.showAtLocation(v, Gravity.CENTER, 0, 0);

                //close the popup window on button click
                closePopupBtn.setOnClickListener(v1 -> popupWindow.dismiss());

                //send the cot
                sendPopupBtn.setOnClickListener(v12 -> {
                    Toast.makeText(getApplicationContext(), "Sending GeoObject CoT", Toast.LENGTH_SHORT).show();
                    // the range to the target
                    double range;
                    if (droneLocationAlt < 1)
                        range = 0.001 / Math.tan(Math.toRadians(gimbalPitch));
                    else
                        range = droneLocationAlt / Math.tan(Math.toRadians(gimbalPitch));

                    // range to horizon, 3.28084ft per meter, 1.169 nautical mile, 1852.001 meters per nautical mile
                    if (gimbalPitch == 0)
                        range = 1.169 * Math.sqrt(droneLocationAlt * 3.28084) * 1852.001;

                    range = Math.abs(range);
                    Log.i(TAG, String.format("GeoObject Range: %f", range));

                    double[] geoObjLatLng = moveLatLng(droneLocationLat, droneLocationLng, range, droneHeading);
                    Log.i(TAG, String.format("Sending geoObject at lat: %f lng: %f", geoObjLatLng[0], geoObjLatLng[1]));

                    // update the count for this name
                    names.put(pickedName[0], howMany[0]+1);

                    new SendCotTask(CompleteWidgetActivity.this).execute("geoObject", FTS_IP, FTS_APIKEY, geoObjLatLng[0], geoObjLatLng[1], pickedAttitude[0], String.format("%s-%d", pickedName[0], howMany[0]), droneHeading);
                    popupWindow.dismiss();
                });
            }
            return false;
        });

        if (object_detect) {
            objectDetector_handler.postDelayed(objectDetector_runnable = () -> {
                objectDetector_handler.postDelayed(objectDetector_runnable, 500);
                Thread objectDetect = new Thread(() -> runObjectDetection());
                if (!objectDetect.isAlive())
                    try {
                        objectDetect.start();
                        //objectDetect.join();
                    } catch(Exception e) {
                        Log.i(TAG, String.format("Thread error: %s",e));
                    }
            }, 500);
        }

        boolean controller_status = initFlightController();
        boolean gimbal_status = initGimbal();
        if (controller_status) {
            if (gimbal_status) {
                Dictionary droneFoVs = new Hashtable();
                droneFoVs.put("UNKNOWN_AIRCRAFT","0");
                droneFoVs.put("INSPIRE_1","72");
                droneFoVs.put("INSPIRE_1_PRO","72");
                droneFoVs.put("INSPIRE_1_RAW","72");
                droneFoVs.put("INSPIRE_2","72");
                droneFoVs.put("PHANTOM_3_PROFESSIONAL","94");
                droneFoVs.put("PHANTOM_3_ADVANCED","94");
                droneFoVs.put("PHANTOM_3_STANDARD","94");
                droneFoVs.put("Phantom_3_4K","94");
                droneFoVs.put("PHANTOM_4","94");
                droneFoVs.put("PHANTOM_4_PRO","94");
                droneFoVs.put("PHANTOM_4_PRO_V2","94");
                droneFoVs.put("P_4_MULTISPECTRAL","62.7");
                droneFoVs.put("MAVIC_AIR_2","84");
                droneFoVs.put("MAVIC_2_ENTERPRISE_ADVANCED","84");
                droneFoVs.put("MAVIC_PRO","78.8");
                droneFoVs.put("Spark","81.9");
                droneFoVs.put("MAVIC_AIR","85");
                droneFoVs.put("MAVIC_2_PRO","77");
                droneFoVs.put("MAVIC_2_ZOOM","83");
                droneFoVs.put("MAVIC_2","77");
                droneFoVs.put("MAVIC_2_ENTERPRISE","82.6");
                droneFoVs.put("MAVIC_2_ENTERPRISE_DUAL","85");
                droneFoVs.put("MAVIC_MINI","83");

                // dynamically determine what the camera lenses FoV is
                String camera_fov = droneFoVs.get(DJISDKManager.getInstance().getProduct().getModel().toString()).toString();

                // send sensor CoTs while this activity is open and the drone has real gps data
                sensor_handler.postDelayed(sensor_runnable = () -> {
                    sensor_handler.postDelayed(sensor_runnable, sensor_delay);
                    if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                        Log.i(TAG, String.format("Updating drone position: alt %f long %f lat %f distance %f heading %f", droneLocationAlt, droneLocationLng, droneLocationLat, droneDistance, droneHeading));
                        Log.i(TAG, String.format("gimbal stats: pitch: %f roll %f yaw %f", gimbalPitch, gimbalRoll, gimbalYaw));
                        Log.i(TAG, "Sending UAS Location CoT");
                        new SendCotTask(CompleteWidgetActivity.this).execute("sensor", FTS_IP, FTS_APIKEY, drone_name, droneLocationAlt, droneLocationLat, droneLocationLng, droneDistance, droneHeading, camera_fov, gimbalPitch, gimbalRoll, gimbalYaw, gimbalYawRelativeToAircraftHeading);
                        if (getDroneSPI() != null) {
                            // the range to the target, using angle of depression
                            double range;
                            if (droneLocationAlt == 0)
                                range = 0.001 / Math.tan(Math.toRadians(gimbalPitch));
                            else
                                range = droneLocationAlt / Math.tan(Math.toRadians(gimbalPitch));

                            // range to horizon, 3.28084ft per meter, 1.169 nautical mile, 1852.001 meters per nautical mile
                            if (gimbalPitch == 0)
                                range = 1.169 * Math.sqrt(droneLocationAlt*3.28084) * 1852.001;

                            range = Math.abs(range);
                            Log.i(TAG, String.format("SPI Range: %f",range));

                            double[] spiLatLng = moveLatLng(droneLocationLat, droneLocationLng, range, droneHeading);
                            Log.i(TAG, String.format("Sending SPI lat: %f lng: %f",spiLatLng[0], spiLatLng[1]));
                            new SendCotTask(CompleteWidgetActivity.this).execute("SPI", FTS_IP, FTS_APIKEY, spiLatLng[0], spiLatLng[1], String.format("%s SPI",drone_name));
                        }
                    } else {
                        Log.i(TAG, "No GPS, retrying...");
                    }
                }, sensor_delay);
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

    private void runObjectDetection() {
        try {
            Bitmap bitmap = fpvWidget.getBitmap();
            if (bitmap == null) {
                Log.i(TAG, "fpvWidget.getBitmap returned null");
                return;
            }

            // Creates inputs for reference.
            TensorImage image = TensorImage.fromBitmap(bitmap);
            // Run inference
            List<Detection> results = objectDetector.detect(image);

            final List<Detector.Recognition> mappedRecognitions = new ArrayList<>();
            for (Detection result : results) {
                final RectF location = result.getBoundingBox();
                List<Category> labels = result.getCategories();
                for (Category label : labels) {
                    final float score = label.getScore();
                    final int id = label.getIndex();
                    final String title = label.getLabel();
                    mappedRecognitions.add(new Detector.Recognition(String.valueOf(id), title, score, location));
                }
            }

            tracker.trackResults(mappedRecognitions, ++timestamp);
            trackingOverlay.postInvalidate();

        } catch (Exception e) {
            Log.i(TAG, String.format("Something bad happened doing TFLite: %s", e));
        }
    }

    private boolean isFlightControllerSupported() {
        return DJISDKManager.getInstance().getProduct() != null &&
                DJISDKManager.getInstance().getProduct() instanceof Aircraft &&
                ((Aircraft) DJISDKManager.getInstance().getProduct()).getFlightController() != null;
    }

    public static boolean checkGpsCoordinates(double latitude, double longitude) {
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

    public static double[] moveLatLng(double latitude, double longitude, double range, double bearing) {
        double EarthRadius = 6378137.0;
        double DegreesToRadians = Math.PI / 180.0;
        double RadiansToDegrees = 180.0 / Math.PI;

        final double latA = latitude * DegreesToRadians;
        final double lonA = longitude * DegreesToRadians;
        final double angularDistance = range / EarthRadius;
        final double trueCourse = bearing * DegreesToRadians;

        final double lat = Math.asin(
                Math.sin(latA) * Math.cos(angularDistance) +
                        Math.cos(latA) * Math.sin(angularDistance) * Math.cos(trueCourse));

        final double dlon = Math.atan2(
                Math.sin(trueCourse) * Math.sin(angularDistance) * Math.cos(latA),
                Math.cos(angularDistance) - Math.sin(latA) * Math.sin(lat));

        final double lon = ((lonA + dlon + Math.PI) % (Math.PI * 2)) - Math.PI;

        return new double[] {lat * RadiansToDegrees, lon * RadiansToDegrees};
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
    //Haversine Distance Algorithm
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
                    gimbalRoll  = attitude.getRoll();
                    gimbalYaw   = attitude.getYaw();
                    gimbalYawRelativeToAircraftHeading = gimbalCurrentState.getYawRelativeToAircraftHeading();
                }
            });
            return true;
        }
        else {
            return false;
        }
    }

    public void setDroneSPI(String uid) {
        DRONE_SPI_UID = uid;
    }

    public String getDroneSPI() {
        return DRONE_SPI_UID;
    }

    public void setDroneGUID(String uid) {
        FTS_DRONE_UID = uid;
    }

    public String getDroneGUID() {
        return FTS_DRONE_UID;
    }

    @Override
    protected void onPause() {
        // stop the sensor cot generation
        Log.i(TAG, "Stopping sensor thread");
        Toast.makeText(getApplicationContext(), "Stopping UAS Location Updates", Toast.LENGTH_SHORT).show();
        sensor_handler.removeCallbacks(sensor_runnable);
        objectDetector_handler.removeCallbacks(objectDetector_runnable);
        // stop the stream
        if (l!=null && l.isStreaming()) {
            Log.i(TAG, "Stopping stream thread");
            Toast.makeText(getApplicationContext(), "Stopping Stream", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(getApplicationContext(), "Stopping UAS Location Updates", Toast.LENGTH_SHORT).show();
        sensor_handler.removeCallbacks(sensor_runnable);
        objectDetector_handler.removeCallbacks(objectDetector_runnable);
        // stop the stream
        if (l!=null && l.isStreaming()) {
            Log.i(TAG, "Stopping stream thread");
            Toast.makeText(getApplicationContext(), "Stopping Stream", Toast.LENGTH_SHORT).show();
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
