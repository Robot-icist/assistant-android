package com.assistant.main.helpers;

import android.os.AsyncTask;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpRequestHelper {

    public interface HttpResponseCallback {
        void onResponse(String result) throws JSONException; // Called when the request succeeds
        void onError(String error); // Called when there is an error
    }

    // This function uses AsyncTask to make the GET request in the background
    public static void sendHttpGetRequest(String urlString, HttpResponseCallback callback) {
        new HttpGetRequestTask(callback).execute(urlString);
    }

    // This function uses AsyncTask to make the POST request in the background
    public static void sendHttpPostRequest(String urlString, String jsonData, HttpResponseCallback callback) {
        new HttpPostRequestTask(callback).execute(urlString, jsonData);
    }

    // AsyncTask for performing the HTTP GET request on a background thread
    private static class HttpGetRequestTask extends AsyncTask<String, Void, String> {
        private HttpResponseCallback callback;

        public HttpGetRequestTask(HttpResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... urls) {
            String result = null;
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);

                int statusCode = urlConnection.getResponseCode();
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    result = response.toString();
                } else {
                    result = "Error: " + statusCode;
                }

                urlConnection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                result = e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (callback != null) {
                try {
                    callback.onResponse(result);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // AsyncTask for performing the HTTP POST request on a background thread
    private static class HttpPostRequestTask extends AsyncTask<String, Void, String> {
        private HttpResponseCallback callback;

        public HttpPostRequestTask(HttpResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... params) {
            String result = null;
            try {
                String urlString = params[0];
                String jsonData = params[1];

                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);

                // Set the Content-Type and Accept headers to application/json
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");

                // Enable input/output streams
                urlConnection.setDoOutput(true);

                // Write the data to the output stream
                try (DataOutputStream writer = new DataOutputStream(urlConnection.getOutputStream())) {
                    writer.writeBytes(jsonData);
                    writer.flush();
                }

                int statusCode = urlConnection.getResponseCode();
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    result = response.toString();
                } else {
                    result = "Error: " + statusCode;
                }

                urlConnection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                result = e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (callback != null) {
                try {
                    callback.onResponse(result);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
