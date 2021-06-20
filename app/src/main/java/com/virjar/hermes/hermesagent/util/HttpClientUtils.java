package com.virjar.hermes.hermesagent.util;

import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by zhuyin on 2018/3/28.
 */

public class HttpClientUtils {
    private static OkHttpClient client;

    private HttpClientUtils() {
        ConnectionPool connectionPool = new ConnectionPool(3, 3, TimeUnit.SECONDS);
        client = new OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.SECONDS)
                .connectTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .connectionPool(connectionPool)
                .retryOnConnectionFailure(false)
                .build();
    }

    private static OkHttpClient getClient() {
        if (client != null) {
            return client;
        }
        synchronized (HttpClientUtils.class) {
            if (client == null) {
                new HttpClientUtils();
            }
        }
        return client;
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static String getRequest(String url) {

        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();

        Response response = null;
        try {
            response = getClient().newCall(request).execute();
            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    return null;
                }
                return responseBody.string();
            }
            return null;
        } catch (IOException e) {
            Log.e("httpclient", "getRequest failed", e);
            return null;
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    public static String postJSON(String url, String json) {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = null;
        try {
            response = getClient().newCall(request).execute();
            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    return responseBody.string();
                }
            }
            return null;
        } catch (IOException e) {
            Log.e("httpclient", "postJSON failed", e);
            return null;
        } finally {
            IOUtils.closeQuietly(response);
        }
    }
}
