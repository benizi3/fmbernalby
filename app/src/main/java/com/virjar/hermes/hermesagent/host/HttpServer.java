package com.virjar.hermes.hermesagent.host;

import android.content.Context;
import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.host.manager.StartAppTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

/**
 * Created by virjar on 2018/8/23.
 */

public class HttpServer {

    private static final String TAG = "httpServer";
    private static AsyncHttpServer server = null;
    private static AsyncServer mAsyncServer = null;
    private static HttpServer instance = new HttpServer();
    private int httpServerPort = 0;

    private HttpServer() {
    }

    public static HttpServer getInstance() {
        return instance;
    }

    public int getHttpServerPort() {
        return httpServerPort;
    }

    public void startServer(Context context) {
        if (CommonUtils.pingServer()) {
            return;
        }
        stopServer();
        server = new AsyncHttpServer();
        mAsyncServer = new AsyncServer();

        bindPingCommand(server);
        bindStartAppCommand(server, context);

        try {
            httpServerPort = Constant.httpServerPort;
            server.listen(mAsyncServer, httpServerPort);
            Log.i(TAG, "start server success...");
        } catch (Exception e) {
            Log.e(TAG, "startServer error", e);
        }
    }

    public synchronized void stopServer() {
        if (server == null) {
            return;
        }
        server.stop();
        mAsyncServer.stop();
        server = null;
        mAsyncServer = null;
    }

    private void bindStartAppCommand(AsyncHttpServer server, final Context context) {
        server.get(Constant.startAppPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String app = request.getQuery().getString("app");
                if (StringUtils.isBlank(app)) {
                    CommonUtils.sendJSON(response, CommonRes.failed("the param {app} must exist"));
                    return;
                }
                new StartAppTask(app, context, response).execute();
            }
        });
    }


    private void bindPingCommand(AsyncHttpServer server) {
        server.get(Constant.httpServerPingPath, new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send("true");
            }
        });
    }
}
