package com.virjar.hermes.hermesagent.host.http;

import android.os.DeadObjectException;
import android.os.RemoteException;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.JSONArrayBody;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.virjar.hermes.hermesagent.hermes_api.CommonRes;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IHookAgentService;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.host.thread.J2Executor;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Map;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/24.<br>
 * 处理外部RPC请求的handler
 */
@Slf4j
public class RPCInvokeCallback implements HttpServerRequestCallback {
    private FontService fontService;
    private J2Executor j2Executor;

    RPCInvokeCallback(FontService fontService, J2Executor j2Executor) {
        this.fontService = fontService;
        this.j2Executor = j2Executor;
    }

    @Override
    public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        log.info("hand a rpc invoke request");
        if (!CommonUtils.xposedStartSuccess) {
            log.error("hermes startup failed,xposed module not inject");
            CommonUtils.sendJSON(response, CommonRes.failed("hermes startup failed,please contact hermes administrator; android device info{mac: " +
                    CommonUtils.deviceID(fontService) + " ,ip:" + APICommonUtils.getLocalIp() + "}"));
            return;
        }
        Map<String, String> innerParam = determineInnerParam(request);
        final String invokePackage = innerParam.get(Constant.invokePackage);
        if (StringUtils.isBlank(invokePackage)) {
            log.warn(Constant.needInvokePackageParamMessage);
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_need_invoke_package_param, Constant.needInvokePackageParamMessage));
            return;
        }
        final IHookAgentService hookAgent = fontService.findHookAgent(invokePackage);
        if (hookAgent == null) {
            log.warn(Constant.serviceNotAvailableMessage);
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
            return;
        }

        final InvokeRequest invokeRequest = buildInvokeRequest(request, innerParam);
        if (invokeRequest == null) {
            log.error("unknown request data format");
            CommonUtils.sendJSON(response, CommonRes.failed("unknown request data format"));
            return;
        }
        new J2ExecutorWrapper(j2Executor.getOrCreate(invokePackage, 2, 4),
                new Runnable() {
                    @Override
                    public void run() {
                        InvokeResult invokeResult = null;
                        long invokeStartTimestamp = System.currentTimeMillis();
                        try {
                            String logMessage = " startTime: " + invokeStartTimestamp + "  params:" + invokeRequest.getParamContent(false);
                            APICommonUtils.requestLogI(invokeRequest, logMessage);
                            log.info(logMessage);
                            invokeResult = hookAgent.invoke(invokeRequest);
                            if (invokeResult == null) {
                                APICommonUtils.requestLogW(invokeRequest, " agent return null object");
                                CommonUtils.sendJSON(response, CommonRes.failed("agent return null object"));
                                return;
                            }
                            if (invokeResult.getStatus() != InvokeResult.statusOK) {
                                APICommonUtils.requestLogW(invokeRequest, " return status not ok");
                                CommonUtils.sendJSON(response, CommonRes.failed(invokeResult.getStatus(), invokeResult.getTheData()));
                                return;
                            }
                            if (invokeResult.getDataType() == InvokeResult.dataTypeJson) {
                                CommonUtils.sendJSON(response, CommonRes.success(JSON.parse(invokeResult.getTheData())));
                            } else {
                                CommonUtils.sendJSON(response, CommonRes.success(invokeResult.getTheData()));
                            }
                        } catch (DeadObjectException e) {
                            APICommonUtils.requestLogW(invokeRequest, "service " + invokePackage + " dead ,offline it", e);
                            fontService.releaseDeadAgent(invokePackage);
                            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_service_not_available, Constant.serviceNotAvailableMessage));
                        } catch (RemoteException e) {
                            APICommonUtils.requestLogW(invokeRequest, "remote exception", e);
                            CommonUtils.sendJSON(response, CommonRes.failed(e));
                        } finally {
                            long endTime = System.currentTimeMillis();
                            APICommonUtils.requestLogI(invokeRequest, "invoke end time:" + endTime + " duration:" + ((endTime - invokeStartTimestamp) / 1000) + "s");
                            if (invokeResult != null) {
                                String logMessage = "invoke result: " + invokeResult.getTheData();
                                APICommonUtils.requestLogI(invokeRequest, logMessage);
                                log.info(logMessage);
                                String needDeleteFile = invokeResult.needDeleteFile();
                                if (needDeleteFile != null) {
                                    try {
                                        hookAgent.clean(needDeleteFile);
                                    } catch (DeadObjectException e) {
                                        fontService.releaseDeadAgent(invokePackage);
                                    } catch (RemoteException e) {
                                        APICommonUtils.requestLogW(invokeRequest, "remove temp file failed", e);
                                    }
                                }
                            }
                        }
                    }
                }, response).run();

    }

    private Map<String, String> determineInnerParam(AsyncHttpServerRequest request) {
        Multimap query = request.getQuery();
        String invokePackage = query.getString(Constant.invokePackage);
        String invokeSessionID = query.getString(Constant.invokeSessionID);
        Map<String, String> result = Maps.newHashMap();
        if (StringUtils.isNotBlank(invokePackage)) {
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
        } else if (o instanceof Multimap) {
            query = (Multimap) o;
            invokePackage = query.getString(Constant.invokePackage);
            invokeSessionID = query.getString(Constant.invokeSessionID);
        }
        result.put(Constant.invokePackage, invokePackage);
        result.put(Constant.invokeSessionID, invokeSessionID);
        return result;
    }

    private Joiner joiner = Joiner.on('&').skipNulls();

    private String joinParam(Multimap params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return joiner.join(Iterables.transform(params, new Function<NameValuePair, String>() {
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
        if (StringUtils.isBlank(requestSession)) {
            requestSession = CommonUtils.genRequestID();
        }
        if (!requestSession.startsWith("request_session_")) {
            requestSession += "request_session_";
        }
        if ("get".equalsIgnoreCase(request.getMethod())) {
            return new InvokeRequest(joinParam(request.getQuery()), fontService, requestSession);
        }

        AsyncHttpRequestBody requestBody = request.getBody();
        if (requestBody instanceof UrlEncodedFormBody) {
            return new InvokeRequest(joiner.join(joinParam(request.getQuery()),
                    joinParam(((UrlEncodedFormBody) requestBody).get())), fontService, requestSession);
        }
        if (requestBody instanceof StringBody) {
            return new InvokeRequest(((StringBody) requestBody).get(), fontService, requestSession);
        }
        if (requestBody instanceof JSONObjectBody) {
            JSONObjectBody jsonObjectBody = (JSONObjectBody) requestBody;
            JSONObject jsonObject = jsonObjectBody.get();
            return new InvokeRequest(jsonObject.toString(), fontService, requestSession);
        }

        if (request instanceof JSONArrayBody) {
            return new InvokeRequest(((JSONArrayBody) request).get().toString(), fontService, requestSession);
        }
        return null;
    }


}