package com.virjar.hermes.hermesagent;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.common.base.Joiner;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by virjar on 2018/9/7.<br>
 * for FlowManager
 */

public class HermesApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FlowManager.init(this);
        fixXposedConfigFile();
    }

    private static String xposedModuleConfigFile = Constant.XPOSED_BASE_DIR + "conf/modules.list";

    private void fixXposedConfigFile() {
        if (!CommonUtils.isSuAvailable()) {
            Log.w("weijia", "need root permission ");
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
            return;
        }
        Iterator<String> iterator = xposedModules.iterator();
        boolean hinted = false;
        while (iterator.hasNext()) {
            String str = iterator.next();
            if (StringUtils.containsIgnoreCase(str, BuildConfig.APPLICATION_ID)) {
                if (StringUtils.equals(sourcePath, sourcePath)) {
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
        Log.i("weijia", "xposed installer模块地址维护有误，hermes自动修复hermes关联代码地址");
        xposedModules.add(sourcePath);
        String newConfig = Joiner.on("\\\n").join(xposedModules);
        Shell.SU.run("echo \"" + newConfig + "\" > " + xposedModuleConfigFile);
    }
}
