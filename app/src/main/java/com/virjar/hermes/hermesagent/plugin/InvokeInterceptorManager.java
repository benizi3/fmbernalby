package com.virjar.hermes.hermesagent.plugin;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.xposed_extention.ClassScanner;
import com.virjar.xposed_extention.Ones;
import com.virjar.xposed_extention.SharedObject;

import java.util.Set;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/9/27.<br>
 * 框架统一插件功能，可以对单个app，实现统一的操作功能
 */
@Slf4j
public class InvokeInterceptorManager {
    private static Set<InvokeInterceptor> invokeInterceptors;

    static {
        invokeInterceptors = Sets.newCopyOnWriteArraySet();
        ClassScanner.SubClassVisitor<InvokeInterceptor> classVisitor = new ClassScanner.SubClassVisitor<>(true, InvokeInterceptor.class);
        ClassScanner.scan(classVisitor, Constant.AGENT_INTERCEPTOR_PACKAGE);
        invokeInterceptors.addAll(Lists.newArrayList(Iterables.filter(Lists.transform(classVisitor.getSubClass(), new Function<Class<? extends InvokeInterceptor>, InvokeInterceptor>() {
            @Nullable
            @Override
            public InvokeInterceptor apply(Class<? extends InvokeInterceptor> input) {
                try {
                    return input.newInstance();
                } catch (Exception e) {
                    log.warn("agent callback plugin load failed", e);
                }
                return null;
            }
        }), new Predicate<InvokeInterceptor>() {
            @Override
            public boolean apply(@Nullable InvokeInterceptor input) {
                return input != null;
            }
        })));
    }

    public static void setUp() {
        Ones.hookOnes(InvokeInterceptorManager.class, "setupAllInterceptor", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                for (InvokeInterceptor invokeInterceptor : invokeInterceptors) {
                    try {
                        log.info("setup invoke interceptor: " + invokeInterceptor.getClass().getName());
                        invokeInterceptor.setup();
                    } catch (Exception e) {
                        invokeInterceptors.remove(invokeInterceptor);
                        log.error("interceptor setup failed", e);
                    }
                }
            }
        });
    }

    static InvokeResult handleIntercept(InvokeRequest invokeRequest) {
        if (invokeInterceptors == null || invokeInterceptors.size() == 0) {
            return null;
        }
        for (InvokeInterceptor invokeInterceptor : invokeInterceptors) {
            InvokeResult invokeResult = invokeInterceptor.intercept(invokeRequest);
            if (invokeResult != null) {
                APICommonUtils.requestLogI(invokeRequest, "the interceptor \"" + invokeInterceptor.getClass().getName() + "\" " +
                        "has a invoke result,invoke maybe return early");
                return invokeResult;
            }
        }
        if (invokeRequest.hasParam(Constant.HERMES_SETTING_INVOKE)) {
            return InvokeResult.success("inner invoke success", SharedObject.context);
        }
        return null;
    }
}
