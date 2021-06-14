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

            JSONObject geoObject = new JSONObject();

            if (CotType.equalsIgnoreCase("sensor")) {
                double altitude = (double) data[4];
                double latitude = (double) data[5];
                double longitude = (double) data[6];
                double distance = (double) data[7];
                double heading = (double) data[8];

                geoObject.put("longitude", longitude);
                geoObject.put("latitude", latitude);
                geoObject.put("distance", distance);
                geoObject.put("bearing", heading);
                geoObject.put("attitude", "friendly");
                geoObject.put("geoObject", "Ground");
                geoObject.put("how", "nonCoT");
                geoObject.put("name", drone_name);
                geoObject.put("timeout", 600);

                if (parent.FTS_GUID == null) {
                    url = new URL("http://" + FTSaddr + "/ManageGeoObject/postGeoObject");
                    method = "POST";
                } else {
                    // PUT method in FTS 1.8 is broken, just use POST with uid
                    //url = new URL("http://"+FTSaddr+":19023/ManageGeoObject/putGeoObject");
                    url = new URL("http://" + FTSaddr + "/ManageGeoObject/postGeoObject");
                    geoObject.put("uid", parent.getDroneGUID());
                    // PUT method in FTS 1.8 is broken, just use POST with uid
                    //method = "PUT";
                    method = "POST";
                }
            } else if (CotType.equalsIgnoreCase("stream")) {
                String[] RTMPaddr = ((String) data[4]).split(":");
                String RTMPpath = (String) data[5];

                geoObject.put("streamAddress",RTMPaddr[0]);
                geoObject.put("streamPort", RTMPaddr[1]);
                geoObject.put("streamPath",RTMPpath);
                geoObject.put("alias", String.format("Drone Stream from %s",drone_name));
                geoObject.put("streamProtocol","rtmp");

                url = new URL("http://" + FTSaddr + "/ManageVideoStream/postVideoStream");
                method = "POST";
            }

            Log.i(TAG, String.format("REST API url: %s", url));

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod(method);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Authorization", APIKEY);

            OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"));
            writer.write(geoObject.toString());
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
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("Content", response);
            jsonObject.put("Message", urlConnection.getResponseMessage());
            jsonObject.put("Length", urlConnection.getContentLength());
            jsonObject.put("Type", urlConnection.getContentType());
            urlConnection.disconnect();
            return jsonObject.toString();
        } catch (Exception e) {
            Log.i(TAG, e.toString());
            return e.toString();
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        Log.i(TAG, "SERVER RESPONSE: " + result);
        Toast.makeText(parent.getApplicationContext(), String.format("SERVER RESPONSE: %s",result), Toast.LENGTH_LONG).show();

        try {
            JSONObject jsonObject = new JSONObject(result);
            if (jsonObject.get("Message").toString().equalsIgnoreCase("OK")) {
                String GUID = jsonObject.get("Content").toString();
                Log.i(TAG,String.format("GUID from FTS: %s", GUID));
                parent.setDroneGUID(GUID);
            }
        } catch (Exception e) {
            Log.i(this.getClass().getName(), e.toString());
        }
    }
}
