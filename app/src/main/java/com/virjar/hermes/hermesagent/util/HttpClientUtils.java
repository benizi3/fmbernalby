package com.quanr.daigou.utils;

import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by zhuyin on 2018/3/28.
 */

public class HttpClientUtils {
    private static OkHttpClient client;

    private HttpClientUtils() {
        ConnectionPool connectionPool = new ConnectionPool(3, 3, TimeUnit.SECONDS);
        client = new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .connectTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .connectionPool(connectionPool)
                .retryOnConnectionFailure(false)
                .build();
    }

    private static OkHttpClient getClient() {
        if (client == null) {
            synchronized (HttpClientUtils.class) {
                if (client == null) {
                    new HttpClientUtils();
                }
            }
        }
        return client;
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static String getRequest(String url) throws IOException {

        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();

        String result = "";
        Response response = null;
        try {
            response = getClient().newCall(request).execute();
            if (response.isSuccessful()) {
                result = response.body().string();
            }
        } catch (IOException e) {
            Log.i("httpclient", "getRequest error_1");
            throw new IOException("request exe");
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static String postRequest(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        String result = "";
        Response response = null;
        try {
            response = getClient().newCall(request).execute();
            if (response.isSuccessful()) {
                result = response.body().string();
            }
        } catch (IOException e) {
            Log.i("httpclient", "postRequest error_1");
            throw new IOException("request exe");
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
}
