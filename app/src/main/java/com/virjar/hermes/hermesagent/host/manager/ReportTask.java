package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.virjar.hermes.hermesagent.bean.ReportModel;
import com.virjar.hermes.hermesagent.host.http.HttpServer;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.HttpClientUtils;
import com.virjar.hermes.hermesagent.util.SamplerUtils;

import java.io.IOException;
import java.util.List;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import se.vidstige.jadb.JadbConnection;
import se.vidstige.jadb.JadbDevice;

/**
 * Created by virjar on 2018/8/24.
 */

public class ReportTask extends TimerTask {
    private static final String tag = "ReportTask";
    private Context context;
    private FontService fontService;

    private static final MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

    private int failedTimes = 0;

    public ReportTask(Context context, FontService fontService) {
        this.context = context;
        this.fontService = fontService;
    }

    @Override
    public void run() {
        ReportModel reportModel = new ReportModel();
        reportModel.setAgentServerIP(CommonUtils.getLocalIp());
        reportModel.setAgentServerPort(HttpServer.getInstance().getHttpServerPort());
        reportModel.setOnlineServices(fontService.onlineAgentServices());
        reportModel.setCpuLoader(SamplerUtils.sampleCPU());
        reportModel.setMemoryInfo(SamplerUtils.sampleMemory(context));

        final Request request = new Request.Builder()
                .url(Constant.serverBaseURL + Constant.reportPath)
                .post(RequestBody.create(mediaType, JSONObject.toJSONString(reportModel)))
                .build();
        HttpClientUtils.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                failedTimes++;
                rebootIfNeed();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                failedTimes = 0;
            }
        });
    }

    private void rebootIfNeed() {
        //TODO 由服务器下发的配置控制
        //TODO 调用成功，记录清零
        if (failedTimes < 15) {
            return;
        }
        JadbConnection jadb = new JadbConnection();
        List<JadbDevice> devices;
        try {
            devices = jadb.getDevices();
        } catch (Exception e) {
            Log.e(tag, "failed to find adb server", e);
            return;
        }
        if (devices.size() == 0) {
            Log.e(tag, "failed to find adb server");
            return;
        }
        for (JadbDevice jadbDevice : devices) {
            Log.i(tag, "reboot device:" + jadbDevice.getSerial());
            try {
                jadbDevice.execute("reboot");
            } catch (Exception e) {
                Log.i(tag, "device reboot failed");
            }
        }

    }
}
