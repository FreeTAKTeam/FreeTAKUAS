package org.FreeTak.FreeTAKUAS;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.amap.api.maps.model.LatLng;
import com.google.gson.annotations.JsonAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import dji.sdk.sdkmanager.DJISDKManager;

import static java.lang.Math.tan;

public class SendCotTask extends AsyncTask<Object, Void, String> {

    private CompleteWidgetActivity parent;

    private String TAG = this.getClass().getName();

    public SendCotTask(CompleteWidgetActivity parent) {
        this.parent = parent;
    }

    @Override
    protected String doInBackground(Object... data) {
        try {
            String method = "POST";
            URL url = null;

            // core fields for all REST API endpoints
            String CotType = (String) data[0];
            String FTSaddr = (String) data[1];
            String APIKEY = "Bearer " + (String) data[2];

            JSONObject jsonObject = new JSONObject();

            if (CotType.equalsIgnoreCase("sensor")) {
                String drone_name = (String) data[3];
                float altitude    = (float) data[4];
                double latitude   = (double) data[5];
                double longitude  = (double) data[6];
                double distance   = (double) data[7];
                double heading    = (double) data[8];
                String camera_fov = (String) data[9];
                float gimbalPitch = (float) data[10];
                float gimbalRoll  = (float) data[11];
                float gimbalYaw   = (float) data[12];
                float gimbalYawRelativeToAircraftHeading = (float) data[13];

                jsonObject.put("name", drone_name);
                jsonObject.put("Bearing", String.valueOf(heading));
                jsonObject.put("longitude", longitude);
                jsonObject.put("latitude", latitude);
                jsonObject.put("VideoURLUID", parent.RTMP_URL);

                // the range to the target
                // ref: https://stonekick.com/blog/using-basic-trigonometry-to-measure-distance.html
                double range;
                if (altitude == 0)
                    range = 0.001 / Math.tan(Math.toRadians(gimbalPitch));
                else
                    range = altitude / Math.tan(Math.toRadians(gimbalPitch));

                // range to horizon, 3.28084ft per meter, 1.169 nautical mile, 1852.001 meters per nautical mile
                if (gimbalPitch == 0)
                    range = 1.169 *  Math.sqrt(altitude*3.28084) * 1852.001;

                range = Math.abs(range);
                jsonObject.put("Range", String.valueOf(range));

                double fov = Math.abs(2 * (tan(Math.toRadians(gimbalPitch)/2) * range));
                jsonObject.put("FieldOfView", String.valueOf(fov));

                if (parent.getDroneSPI() == null) {
                    LatLng spiLatLng = parent.moveLatLng(new LatLng(latitude,longitude), range, heading);
                    jsonObject.put("SPILatitude", spiLatLng.latitude);
                    jsonObject.put("SPILongitude", spiLatLng.longitude);
                    jsonObject.put("SPIName", String.format("%s_SPI", drone_name));
                }

                url = new URL("http://" + FTSaddr + "/Sensor/postDrone");

                if (parent.getDroneGUID() != null) {
                    // FTS bug, not supporting put
                    //method = "PUT";
                    //url = new URL("http://" + FTSaddr + "/Sensor/putDrone");
                    jsonObject.put("uid", parent.getDroneGUID());
                }
            } else if (CotType.equalsIgnoreCase("SPI")) {
                double spi_latitude  = (double) data[3];
                double spi_longitude = (double) data[4];
                String name          = (String) data[5];

                jsonObject.put("uid", parent.getDroneSPI());
                jsonObject.put("timeout",300);
                jsonObject.put("longitude", spi_longitude);
                jsonObject.put("latitude", spi_latitude);
                jsonObject.put("droneUid", parent.getDroneGUID());
                jsonObject.put("name", name);

                url = new URL("http://" + FTSaddr + "/Sensor/postSPI");
            } else if (CotType.equalsIgnoreCase("geoObject")) {
                double latitude  = (double) data[3];
                double longitude = (double) data[4];
                String attitude  = (String) data[5];
                String name      = (String) data[6];
                double droneHeading = (double) data[7];

                jsonObject.put("longitude", longitude);
                jsonObject.put("latitude", latitude);
                jsonObject.put("attitude", attitude);
                jsonObject.put("name", name);
                jsonObject.put("how", "nonCoT");
                jsonObject.put("geoObject", "Ground");
                jsonObject.put("Bearing", droneHeading);
                jsonObject.put("timeout",6000);

                url = new URL("http://" + FTSaddr + "/ManageGeoObject/postGeoObject");
            } else if (CotType.equalsIgnoreCase("start_stream")) {
                String drone_name = (String) data[3];
                String[] RTMPaddr = ((String) data[4]).split(":");

                jsonObject.put("streamAddress",RTMPaddr[0]);
                jsonObject.put("streamPort", RTMPaddr[1]);
                jsonObject.put("streamPath",String.format("/live/UAS-%s", drone_name));
                jsonObject.put("alias", String.format("Video stream from %s",drone_name));
                jsonObject.put("streamProtocol","rtmp");

                url = new URL("http://" + FTSaddr + "/ManageVideoStream/postVideoStream");
            }

            Log.i(TAG, String.format("Sending COT: %s", jsonObject.toString()));

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod(method);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Authorization", APIKEY);

            OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"));
            writer.write(jsonObject.toString());
            writer.flush();
            writer.close();
            outputStream.close();

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
            return ResponseObject.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        Log.i(TAG, "SERVER RESPONSE: " + result);
        //Toast.makeText(parent.getApplicationContext(), String.format("SERVER RESPONSE: %s",result), Toast.LENGTH_LONG).show();

        try {
            JSONObject jsonObject = new JSONObject(result);
            if (jsonObject.get("Code").toString().equalsIgnoreCase("200")) {
                if (jsonObject.get("Message").toString().equalsIgnoreCase("OK")) {
                    try {
                        JSONObject GUID = new JSONObject(jsonObject.get("Content").toString());
                        String drone_guid = GUID.get("uid").toString();
                        String spi_guid = GUID.get("SPI_uid").toString();
                        Log.i(TAG, String.format("uid from FTS: %s", drone_guid));
                        Log.i(TAG, String.format("SPI_uid from FTS: %s", spi_guid));
                        parent.setDroneGUID(drone_guid);
                        parent.setDroneSPI(spi_guid);
                        Log.i(TAG, String.format("Set uid to %s", parent.getDroneGUID()));
                        Log.i(TAG, String.format("Set SPI_uid to %s", parent.getDroneSPI()));
                    } catch (JSONException e) {
                        Log.i(TAG, String.format("GeoObject|VideoStream|SPI UID: %s", jsonObject.get("Content").toString()));
                    }
                }
            }
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }
    }
}
