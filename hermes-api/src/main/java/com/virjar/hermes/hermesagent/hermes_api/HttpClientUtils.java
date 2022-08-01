package com.virjar.hermes.hermesagent.hermes_api;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.Lists;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.FormBody;
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
        //由于大量请求都是心跳请求，需要心跳keepAlive，同时考虑心跳时间间隔来确定链接存活时长
        ConnectionPool connectionPool = new ConnectionPool(5, 30, TimeUnit.SECONDS);
        client = new OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.SECONDS)
                .connectTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .connectionPool(connectionPool)
                .retryOnConnectionFailure(false)
                .proxySelector(new ProxySelector() {
                    private List<Proxy> noProxyList = Lists.newArrayList(Proxy.NO_PROXY);

                    @Override
                    public List<Proxy> select(URI uri) {
                        //避免代理导致接口api通信失败
                        return noProxyList;
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

                    }
                }).dispatcher(new Dispatcher(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                        new SynchronousQueue<Runnable>(), new ThreadFactory() {
                    @Override
                    public Thread newThread(@NonNull Runnable runnable) {
                        Thread result = new Thread(runnable, "OkHttp Dispatcher");
                        result.setDaemon(false);
                        //这里，如果网络handler发生了异常，那么只记录日志，而不进行程序中断
                        result.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                            @Override
                            public void uncaughtException(Thread t, Throwable e) {
                                Log.e("weijia", "network callback exception", e);
                            }
                        });
                        //result.setUncaughtExceptionHandler(LogedExceptionHandler.wrap(null));
                        return result;
                    }
                })))
                .build();
    }

    public static OkHttpClient getClient() {
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

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf8");

    public static String get(String url) {
        return executeSync(getRequest(url));
    }

    public static Request getRequest(String url) {
        return new Request.Builder()
                .get()
                .url(url)
                .build();
    }

    public static String postJSON(String url, String json) {
        return executeSync(postJsonRequest(url, json));
    }

    public static Request postJsonRequest(String url, String json) {
        RequestBody body = RequestBody.create(JSON, json);
        return new Request.Builder()
                .url(url)
                .post(body)
                .build();
    }

    public static String post(String url, Map<String, String> param) {
        return executeSync(postRequest(url, param));
    }

    public static Request postRequest(String url, Map<String, String> param) {
        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : param.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        return new Request.Builder().url(url).post(builder.build()).build();
    }

    private static String executeSync(Request request) {
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
            return null;
        } finally {
            IOUtils.closeQuietly(response);
        }
    }
}
