package com.virjar.hermes.hermesagent;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.common.base.Joiner;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.LogConfigurator;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.libsuperuser.Shell;

import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/9/7.<br>
 * for FlowManager
 */
@Slf4j
public class HermesApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogConfigurator.configure(this);
        FlowManager.init(this);
        fixXposedConfigFile();
        if (!CommonUtils.xposedStartSuccess) {
            log.error("component startup failed");
        }
        //rebootIfXposedStartupFailed();
        setNotifyChannel();
    }

    private void setNotifyChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel notificationChannel = new NotificationChannel(BuildConfig.APPLICATION_ID,
                "channel", NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.YELLOW);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(notificationChannel);
    }

    //实践发现，这一招没有效果。。。
    public void rebootIfXposedStartupFailed() {
        if (!CommonUtils.isSuAvailable()) {
            log.warn("need root permission ");
            return;
        }
        if (CommonUtils.isLocalTest()) {
            return;
        }
        if (!CommonUtils.xposedStartSuccess) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    log.warn("xposed module startup failed ,reboot device");
                    Shell.SU.run("reboot");
                }
            }, 120 * 1000);

        }
    }

    private static String xposedModuleConfigFile = Constant.XPOSED_BASE_DIR + "conf/modules.list";

    private void fixXposedConfigFile() {
        if (!CommonUtils.isSuAvailable()) {
            log.warn("need root permission ");
            return;
        }
        String sourcePath;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_META_DATA);
            sourcePath = packageInfo.applicationInfo.sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            //will not happen
            throw new IllegalStateException(e);
        }

        if (StringUtils.isBlank(sourcePath)) {
            return;
        }


        List<String> xposedModules = Shell.SU.run("cat " + xposedModuleConfigFile);
        if (xposedModules.size() == 0) {
            log.info("xposed config file empty");
            return;
        }
        Iterator<String> iterator = xposedModules.iterator();
        boolean hinted = false;
        while (iterator.hasNext()) {
            String str = iterator.next();
            if (StringUtils.containsIgnoreCase(str, BuildConfig.APPLICATION_ID)) {
                if (StringUtils.equals(sourcePath, sourcePath)) {
                    log.info("xposed 配置正常");
                    return;
                } else {
                    iterator.remove();
                    hinted = true;
                    break;
                }
            }
        }
        if (!hinted) {
            return;
        }
        log.info("xposed installer模块地址维护有误，hermes自动修复hermes关联代码地址");
        xposedModules.add(sourcePath);
        String newConfig = Joiner.on("\\\n").join(xposedModules);
        Shell.SU.run("echo \"" + newConfig + "\" > " + xposedModuleConfigFile);
    }
}
