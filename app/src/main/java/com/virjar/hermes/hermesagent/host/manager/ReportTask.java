package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.virjar.hermes.hermesagent.bean.ReportModel;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.host.http.HttpServer;
import com.virjar.hermes.hermesagent.host.http.HttpServerStartupEvent;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel_Table;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.HttpClientUtils;
import com.virjar.hermes.hermesagent.util.SamplerUtils;
import com.virjar.hermes.hermesagent.util.libsuperuser.Shell;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by virjar on 2018/8/24.
 */
@Slf4j
public class ReportTask extends LoggerTimerTask {
    private Context context;
    private FontService fontService;

    private static final MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

    private int failedTimes = 0;

    public ReportTask(Context context, FontService fontService) {
        this.context = context;
        this.fontService = fontService;
    }

    private void report() {
        ReportModel reportModel = new ReportModel();
        reportModel.setAgentServerIP(APICommonUtils.getLocalIp());
        reportModel.setAgentServerPort(HttpServer.getInstance().getHttpServerPort());
        reportModel.setOnlineServices(fontService.onlineAgentServices());
        reportModel.setCpuLoader(SamplerUtils.sampleCPU());
        reportModel.setMemoryInfo(SamplerUtils.sampleMemory(context));
        reportModel.setMac(CommonUtils.deviceID(context));
        reportModel.setBrand(Build.BRAND);
        reportModel.setSystemVersion(Build.VERSION.SDK);

        String reportContent = JSONObject.toJSONString(reportModel);
        log.info("device report request,url:{} body:{}", Constant.serverBaseURL + Constant.reportPath, reportContent);
        final Request request = new Request.Builder()
                .url(Constant.serverBaseURL + Constant.reportPath)
                .post(RequestBody.create(mediaType, reportContent))
                .build();
        HttpClientUtils.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                failedTimes++;
                log.error("report failed ", e);
                rebootIfNeed();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                failedTimes = 0;
                String responseContent = "";
                ResponseBody body = response.body();
                if (body != null) {
                    responseContent = body.string();
                }
                log.info("report response:{}", responseContent);
                if (StringUtils.isBlank(responseContent)) {
                    return;
                }
                handleServiceConfig(responseContent);
            }
        });
    }

    @Override
    public void doRun() {
        //http服务没有启动起来，就不要上报，因为这个时候服务还不是可用状态
        HttpServer.getInstance().onHttpServerStartUp(new HttpServerStartupEvent() {
            @Override
            public void onHttpServerStartUp(HttpServer httpServer) {
                report();
            }
        });
    }

    private synchronized void handleServiceConfig(String configData) {
        JSONObject jsonObject = JSONObject.parseObject(configData);
        if (jsonObject.getIntValue("status") != 0) {
            log.error(jsonObject.getString("message"));
            return;
        }
        Map<String, ServiceModel> historyModelMap = Maps.newHashMap();
        for (ServiceModel serviceModel : SQLite.select().from(ServiceModel.class).queryList()) {
            historyModelMap.put(serviceModel.getTargetAppPackage(), serviceModel);
        }

        JSONArray serviceList = jsonObject.getJSONArray("data");
        String deviceID = CommonUtils.deviceID(context);
        for (int i = 0; i < serviceList.size(); i++) {
            JSONObject serviceItem = serviceList.getJSONObject(i);
            String deviceMac = serviceItem.getString("deviceMac");
            if (!StringUtils.equals(deviceMac, deviceID)) {
                log.warn("get an unrecognized device config {},ignore it", deviceMac);
                continue;
            }
            ServiceModel serviceModel = SQLite.select().from(ServiceModel.class)
                    .where(ServiceModel_Table.targetAppPackage.is(serviceItem.getString("targetAppPackage"))).querySingle();
            //正常情况就只有一个
            boolean isNew = false;
            if (serviceModel == null) {
                serviceModel = new ServiceModel();
                isNew = true;
            } else {
                if (!serviceModel.getServiceId().equals(serviceItem.getLong("serviceId"))) {
                    ServiceModel remove = historyModelMap.remove(serviceModel.getTargetAppPackage());
                    if (remove != null) {
                        log.info("serviceId change,server maybe redeploy,remove old config");
                        remove.delete();
                        serviceModel = new ServiceModel();
                        isNew = true;

                    }
                }
            }
            boolean needReInstallWrapper = false;
            Long wrapperVersionCode = serviceItem.getLong("wrapperVersionCode");
            if (wrapperVersionCode != null && (wrapperVersionCode > 0)
                    && !wrapperVersionCode.equals(serviceModel.getWrapperVersionCode())) {
                needReInstallWrapper = true;
            }
            serviceModel.setTargetAppPackage(serviceItem.getString("targetAppPackage"));
            serviceModel.setServiceId(serviceItem.getLong("serviceId"));
            serviceModel.setDeviceMac(serviceItem.getString("deviceMac"));
            serviceModel.setStatus(serviceItem.getBoolean("status"));
            if (serviceModel.isStatus()) {
                try {
                    fillData(serviceModel, serviceItem);
                } catch (Exception e) {
                    log.warn("config broken for service:{} ,disable deploy the service", serviceModel.getDeviceMac(), e);
                    serviceModel.setStatus(false);
                }
            }
            if (isNew) {
                log.info("a new config for target app:{},save it", serviceModel.getTargetAppPackage());
                serviceModel.save();
            } else {
                log.info("exist config for target app:{} ,update it ", serviceModel.getTargetAppPackage());
                historyModelMap.remove(serviceModel.getTargetAppPackage());
                serviceModel.update();
            }

            if (needReInstallWrapper) {
                InstallTaskQueue.getInstance().installWrapper(serviceModel, context);
            }
        }
        //disable service，if server not push config
        for (ServiceModel serviceModel : historyModelMap.values()) {
            log.info("the service:{} not configured for admin,so offline it", serviceModel.getTargetAppPackage());
            serviceModel.setStatus(false);
            serviceModel.update();
        }
    }

    private void fillData(ServiceModel serviceModel, JSONObject serviceItem) {
        serviceModel.setTargetAppVersionCode(serviceItem.getLong("targetAppVersionCode"));
        serviceModel.setTargetAppDownloadUrl(serviceItem.getString("targetAppDownloadUrl"));
        serviceModel.setWrapperPackage(serviceItem.getString("wrapperPackage"));
        serviceModel.setWrapperVersionCode(serviceItem.getLong("wrapperVersionCode"));
        serviceModel.setWrapperAppDownloadUrl(serviceItem.getString("wrapperAppDownloadUrl"));
    }

    private void rebootIfNeed() {
        if (failedTimes < 15) {
            return;
        }
        log.info("can not connect hermes admin,maybe network config error,now restart device");
        Shell.SU.run("reboot");
    }
}
