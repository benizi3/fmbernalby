package com.virjar.hermes.hermesagent.plugin.location;

import android.content.ContentResolver;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by virjar on 2018/9/27.<br>
 * 管理定位信息，模拟定位数据
 */

public class LocationMocker {

    // private SharedPreferences

    /**
     * 所有的location class相关hook代码
     */
    private static void locationHook() {
        findAndHookMethod(android.location.Location.class, "hasAccuracy", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(android.location.Location.class, "hasAltitude", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(android.location.Location.class, "hasBearing", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(android.location.Location.class, "hasSpeed", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(android.location.Location.class, "getExtras", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Bundle bundle = (Bundle) param.getResult();
                if (bundle == null) {
                    bundle = new Bundle();
                }
                bundle.putInt("satellites", 12);
                param.setResult(bundle);
            }
        });

        findAndHookMethod(android.location.Location.class, "getLatitude", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                //return LocationMocker.getLatLng().latitude;
                //TODO
                return null;
            }
        });
        findAndHookMethod(android.location.Location.class, "getLongitude", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                // return LocationMocker.getLatLng().longitude;
                //TODO
                return null;
            }
        });
        findAndHookMethod(android.location.Location.class, "getSpeed", XC_MethodReplacement.returnConstant(5.0f));
        findAndHookMethod(android.location.Location.class, "getAccuracy", XC_MethodReplacement.returnConstant(50.0f));
        findAndHookMethod(android.location.Location.class, "getBearing", XC_MethodReplacement.returnConstant(50.0f));
        findAndHookMethod(android.location.Location.class, "getAltitude", XC_MethodReplacement.returnConstant(50.0d));
        findAndHookMethod(android.location.Location.class, "getTimeToFirstFix", XC_MethodReplacement.returnConstant(1080));

        findAndHookMethod(android.provider.Settings.Secure.class, "getString", ContentResolver.class, String.class, new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param)
                    throws Throwable {
                if ("mock_location".equals(param.args[1])) {
                    param.setResult("0");
                }
            }
        });

        findAndHookMethod(android.location.Location.class, "isFromMockProvider", new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable {
                param.setResult(Boolean.FALSE);
            }
        });
    }
}
