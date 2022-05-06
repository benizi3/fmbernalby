package com.virjar.hermes.hermesagent.hermes_api;


import com.alibaba.fastjson.JSON;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.JSONArrayBody;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.xposed_extention.SharedObject;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Map;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/10/15.<br>
 */
@Slf4j
public class EmbedRPCInvokeCallback implements HttpServerRequestCallback {
    private AgentCallback agentCallback = null;

    EmbedRPCInvokeCallback(AgentCallback agentCallback) {
        this.agentCallback = agentCallback;
    }

    @Override
    public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        Map<String, String> innerParam = determineInnerParam(request);
        final String invokePackage = innerParam.get(Constant.invokePackage);
        if (invokePackage == null) {
            log.warn(Constant.needInvokePackageParamMessage);
            sendJSON(response, CommonRes.failed(Constant.status_need_invoke_package_param, Constant.needInvokePackageParamMessage));
            return;
        }
        if (!invokePackage.equals(SharedObject.context.getPackageName())) {
            sendJSON(response, CommonRes.failed("the wrapper only compatible with: " + SharedObject.context.getPackageName() +
                    " your param is :" + invokePackage));
            return;
        }

        final InvokeRequest invokeRequest = buildInvokeRequest(request, innerParam);
        if (invokeRequest == null) {
            log.error("unknown request data format");
            sendJSON(response, CommonRes.failed("unknown request data format"));
            return;
        }

        new Thread("embedHermes-worker") {
            public void run() {
                InvokeResult invokeResult = null;
                long invokeStartTimestamp = System.currentTimeMillis();
                try {
                    String logMessage = " startTime: " + invokeStartTimestamp + "  params:" + invokeRequest.getParamContent(false);
                    APICommonUtils.requestLogI(invokeRequest, logMessage);
                    log.info(logMessage);
                    invokeResult = agentCallback.invoke(invokeRequest);
                    if (invokeResult == null) {
                        APICommonUtils.requestLogW(invokeRequest, " agent return null object");
                        sendJSON(response, CommonRes.failed("agent return null object"));
                        return;
                    }
                    if (invokeResult.getStatus() != InvokeResult.statusOK) {
                        APICommonUtils.requestLogW(invokeRequest, " return status not ok");
                        sendJSON(response, CommonRes.failed(invokeResult.getStatus(), invokeResult.getTheData()));
                        return;
                    }
                    if (invokeResult.getDataType() == InvokeResult.dataTypeJson) {
                        sendJSON(response, CommonRes.success(JSON.parse(invokeResult.getTheData())));
                    } else {
                        sendJSON(response, CommonRes.success(invokeResult.getTheData()));
                    }
                } catch (Throwable t) {
                    log.error("wrapper handle exception:", t);
                    sendJSON(response, CommonRes.failed(t));
                } finally {
                    long endTime = System.currentTimeMillis();
                    APICommonUtils.requestLogI(invokeRequest, "invoke end time:" + endTime + " duration:" + ((endTime - invokeStartTimestamp) / 1000) + "s");
                    if (invokeResult != null) {
                        String logMessage = "invoke result: " + invokeResult.getTheData();
                        APICommonUtils.requestLogI(invokeRequest, logMessage);
                        log.info(logMessage);
                    }
                }
            }
        }.start();

    }

    private Map<String, String> determineInnerParam(AsyncHttpServerRequest request) {
        com.koushikdutta.async.http.Multimap query = request.getQuery();
        String invokePackage = query.getString(Constant.invokePackage);
        String invokeSessionID = query.getString(Constant.invokeSessionID);
        Map<String, String> result = Maps.newHashMap();
        if (invokePackage == null || invokePackage.trim().length() == 0) {
            result.put(Constant.invokePackage, invokePackage);
            result.put(Constant.invokeSessionID, invokeSessionID);
            return result;
        }
        Object o = request.getBody().get();
        if (o instanceof JSONObject) {
            invokePackage = ((JSONObject) o).optString(Constant.invokePackage);
            invokeSessionID = ((JSONObject) o).optString(Constant.invokeSessionID);
        } else if (o instanceof String) {
            try {
                com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject((String) o);
                invokePackage = jsonObject.getString(Constant.invokePackage);
                invokeSessionID = jsonObject.getString(Constant.invokeSessionID);
            } catch (com.alibaba.fastjson.JSONException e) {
                //ignore
            }
        } else if (o instanceof com.koushikdutta.async.http.Multimap) {
            query = (com.koushikdutta.async.http.Multimap) o;
            invokePackage = query.getString(Constant.invokePackage);
            invokeSessionID = query.getString(Constant.invokeSessionID);
        }
        result.put(Constant.invokePackage, invokePackage);
        result.put(Constant.invokeSessionID, invokeSessionID);
        return result;
    }

    private Joiner joiner = Joiner.on('&').skipNulls();

    private String joinParam(com.koushikdutta.async.http.Multimap params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return joiner.join(Iterables.transform(params, new Function<com.koushikdutta.async.http.NameValuePair, String>() {
            @Override
            public String apply(@Nullable NameValuePair input) {
                if (input == null) {
                    return null;
                }
                return URLEncoder.encode(input.getName()) + "=" + URLEncoder.encode(input.getValue());
            }
        }));

    }

    private InvokeRequest buildInvokeRequest(AsyncHttpServerRequest request, Map<String, String> innerParam) {
        String requestSession = innerParam.get(Constant.invokeSessionID);
        if (requestSession == null || requestSession.trim().length() == 0) {
            requestSession = "hermes-embed-session-" + Thread.currentThread().getId() + System.currentTimeMillis();
        }
        if (!requestSession.startsWith("request_session_")) {
            requestSession += "request_session_";
        }
        if ("get".equalsIgnoreCase(request.getMethod())) {
            return new InvokeRequest(joinParam(request.getQuery()), null, requestSession);
        }

        AsyncHttpRequestBody requestBody = request.getBody();
        if (requestBody instanceof UrlEncodedFormBody) {
            return new InvokeRequest(joiner.join(joinParam(request.getQuery()),
                    joinParam(((UrlEncodedFormBody) requestBody).get())), null, requestSession);
        }
        if (requestBody instanceof StringBody) {
            return new InvokeRequest(((StringBody) requestBody).get(), null, requestSession);
        }
        if (requestBody instanceof JSONObjectBody) {
            JSONObjectBody jsonObjectBody = (JSONObjectBody) requestBody;
            JSONObject jsonObject = jsonObjectBody.get();
            return new InvokeRequest(jsonObject.toString(), null, requestSession);
        }

        if (request instanceof JSONArrayBody) {
            return new InvokeRequest(((JSONArrayBody) request).get().toString(), null, requestSession);
        }
        return null;
    }

    private static void sendJSON(AsyncHttpServerResponse response, CommonRes commonRes) {
        response.send(Constant.jsonContentType, com.alibaba.fastjson.JSONObject.toJSONString(commonRes));
    }

}