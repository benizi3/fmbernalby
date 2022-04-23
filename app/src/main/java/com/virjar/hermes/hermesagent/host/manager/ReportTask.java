package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.virjar.hermes.hermesagent.bean.ReportModel;
import com.virjar.hermes.hermesagent.host.http.HttpServer;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.util.HttpClientUtils;
import com.virjar.hermes.hermesagent.util.SamplerUtils;

import java.io.IOException;

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

    @Override
    public void doRun() {
        ReportModel reportModel = new ReportModel();
        reportModel.setAgentServerIP(CommonUtils.getLocalIp());
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
            }
        });
    }

    private void rebootIfNeed() {
        //TODO 由服务器下发的配置控制
        //TODO 调用成功，记录清零
        if (failedTimes < 15) {
            return;
        }
        //CommonUtils.restartAndroidSystem();

    }
}
