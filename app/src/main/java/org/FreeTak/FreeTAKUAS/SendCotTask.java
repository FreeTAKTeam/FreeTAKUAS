package org.FreeTak.FreeTAKUAS;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

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

            String CotType = (String) data[0];
            String FTSaddr = (String) data[1];
            String APIKEY = "Bearer " + (String) data[2];
            String drone_name = (String) data[3];

            JSONObject jsonObject = new JSONObject();

            if (CotType.equalsIgnoreCase("sensor")) {
                double altitude  = (double) data[4];
                double latitude  = (double) data[5];
                double longitude = (double) data[6];
                double distance  = (double) data[7];
                double heading   = (double) data[8];
                double camera_fov  = (double) data[9];
                double gimbalPitch = (double) data[10];
                double gimbalRoll  = (double) data[11];
                double gimbalYaw   = (double) data[12];
                double gimbalYawRelativeToAircraftHeading = (double) data[13];

                // the range to the target
                double range = altitude / tan(gimbalYaw);

                jsonObject.put("name", drone_name);
                jsonObject.put("Bearing", String.valueOf(heading));
                jsonObject.put("longitude", longitude);
                jsonObject.put("latitude", latitude);
                jsonObject.put("FieldOfView", camera_fov);
                jsonObject.put("Range", String.valueOf(range));
                jsonObject.put("VideoURLUID", parent.RTMP_URL);

                // earth diameter 6378.137
                // pi 3.141597
                // (1 / (((2 * 3.141597) / 360) * 6378.137)) / 1000 = 1 meter in degree
                double m = range * 0.00000898314041297;
                double spi_latitude  = latitude +  m;
                double spi_longitude = longitude + m / Math.cos(latitude * (3.141597 / 180));

                jsonObject.put("SPILongitude", spi_latitude);
                jsonObject.put("SPILatitude", spi_longitude);
                jsonObject.put("SPIName", String.format("%s SPI",drone_name));

                if (parent.FTS_GUID == null) {
                    url = new URL("http://" + FTSaddr + "/Sensor/postDrone");
                } else {
                    // FTS bug, not supporting put
                    //url = new URL("http://" + FTSaddr + "/Sensor/putDrone");
                    url = new URL("http://" + FTSaddr + "/Sensor/postDrone");
                    jsonObject.put("uid", parent.getDroneGUID());
                    //method = "PUT";
                }
            } else if (CotType.equalsIgnoreCase("stream")) {
                String[] RTMPaddr = ((String) data[4]).split(":");

                jsonObject.put("streamAddress",RTMPaddr[0]);
                jsonObject.put("streamPort", RTMPaddr[1]);
                jsonObject.put("streamPath",String.format("/live/UAS-%s", drone_name));
                jsonObject.put("alias", String.format("Drone Stream from UAS-%s",drone_name));
                jsonObject.put("streamProtocol","rtmp");

                url = new URL("http://" + FTSaddr + "/ManageVideoStream/postVideoStream");
                method = "POST";
            }

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
                    String GUID = jsonObject.get("Content").toString();
                    Log.i(TAG, String.format("GUID from FTS: %s", GUID));
                    parent.setDroneGUID(GUID);
                }
            }
            String Content = jsonObject.get("Content").toString();
            if (Content.equalsIgnoreCase("this endpoint does not exist")) {
            } else {}
        } catch (Exception e) {
            Log.i(this.getClass().getName(), e.toString());
        }
    }
}
