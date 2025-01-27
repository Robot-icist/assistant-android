package com.assistant.main.helpers;

import android.os.AsyncTask;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpRequestHelper {

    public interface HttpResponseCallback {
        void onResponse(String result) throws JSONException; // Called when the request succeeds
        void onError(String error); // Called when there is an error
    }

    // This function uses AsyncTask to make the request in the background
    public static void sendHttpGetRequest(String urlString, HttpResponseCallback callback) {
        new HttpGetRequestTask(callback).execute(urlString);
    }

    // AsyncTask for performing the HTTP request on a background thread
    private static class HttpGetRequestTask extends AsyncTask<String, Void, String> {
        private HttpResponseCallback callback;

        public HttpGetRequestTask(HttpResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... urls) {
            String result = null;
            try {
                URL url = new URL(urls[0]);  // Convert the string URL to a URL object
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                // Set the request method (GET, POST, etc.)
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(10000);  // 10 seconds timeout
                urlConnection.setReadTimeout(10000);     // 10 seconds timeout

                // Connect and get the response code
                int statusCode = urlConnection.getResponseCode();
                if (statusCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
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
