package com.virjar.hermes.hermesagent.host.manager;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.raizlabs.android.dbflow.sql.language.NameAlias;
import com.raizlabs.android.dbflow.sql.language.Operator;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.HttpClientUtils;
import com.virjar.hermes.hermesagent.util.URLEncodeUtil;

import java.io.IOException;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by virjar on 2018/9/7.<br>
 * 定期拉取服务器配置，以便完成app安装等工作
 */

public class RefreshConfigTask extends TimerTask {
    private Context context;

    public RefreshConfigTask(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        String deviceID = CommonUtils.deviceID(context);
        String requestURL = Constant.serverBaseURL + Constant.getConfigPath + "?mac=" + URLEncodeUtil.escape(deviceID);
        Request request = new Request.Builder()
                .get()
                .url(requestURL)
                .build();
        HttpClientUtils.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w("weijia", "query config failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) {
                    //not happen
                    throw new IllegalStateException("this exception will not happened");
                }
                JSONObject jsonRes = JSONObject.parseObject(body.string());
                JSONArray appList = jsonRes.getJSONArray("data");
                // Set<String> needInstallApps = Sets.newHashSet();
                for (int i = 0; i < appList.size(); i++) {
                    JSONObject serviceItem = appList.getJSONObject(i);
                    ServiceModel serviceModel = SQLite.select().from(ServiceModel.class).where(Operator.op(NameAlias.of("appPackage")).eq(serviceItem.getString("appPackage"))).querySingle();
                    boolean isNew = false;
                    if (serviceModel == null) {
                        isNew = true;
                        serviceModel = new ServiceModel();
                    }
                    serviceModel.setAppPackage(serviceItem.getString("appPackage"));
                    serviceModel.setId(serviceItem.getLong("id"));
                    serviceModel.setDeviceMac(serviceItem.getString("mac"));
                    serviceModel.setStatus(serviceItem.getInteger("status"));
                    if (isNew) {
                        serviceModel.save();
                    } else {
                        serviceModel.update();
                    }
                }
            }
        });
    }
}
