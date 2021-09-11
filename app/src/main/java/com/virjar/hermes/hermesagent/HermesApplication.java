package com.virjar.hermes.hermesagent;

import android.app.Application;

import com.raizlabs.android.dbflow.config.FlowManager;

/**
 * Created by virjar on 2018/9/7.<br>
 * for FlowManager
 */

public class HermesApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FlowManager.init(this);
    }
}
