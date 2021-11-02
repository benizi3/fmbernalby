package com.virjar.hermes.hermesagent.hermes_api;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Created by virjar on 2018/1/3.<br/>hook单例封装
 */

public abstract class SingletonXC_MethodHook extends XC_MethodHook {
    @Override
    public boolean equals(Object obj) {
        //className相同则视为相同，避免多次加载hook
        return getClass().getName().equals(obj.getClass().getName());
    }
}
