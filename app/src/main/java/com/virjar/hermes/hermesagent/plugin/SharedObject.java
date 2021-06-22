package com.virjar.hermes.hermesagent.plugin;

import android.content.Context;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/8/23.
 */

public class SharedObject {
    public static Context context;
    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
}
