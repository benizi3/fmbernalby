package com.virjar.hermes.hermesagent.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.ActionRequestHandler;
import com.virjar.hermes.hermesagent.hermes_api.AgentCallback;
import com.virjar.hermes.hermesagent.hermes_api.AgentRegisterAware;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.EmbedWrapper;
import com.virjar.hermes.hermesagent.hermes_api.LogConfigurator;
import com.virjar.hermes.hermesagent.hermes_api.MultiActionWrapper;
import com.virjar.hermes.hermesagent.hermes_api.MultiActionWrapperFactory;
import com.virjar.hermes.hermesagent.hermes_api.WrapperRegister;
import com.virjar.hermes.hermesagent.host.manager.AgentCleanExchangeFileTimer;
import com.virjar.hermes.hermesagent.host.manager.AgentDaemonTask;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.xposed_extention.ClassScanner;
import com.virjar.xposed_extention.SharedObject;
import com.virjar.xposed_extention.XposedExtensionInstaller;

import net.dongliu.apk.parser.ApkFile;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import javax.annotation.Nullable;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2017/12/21.<br/>??????????????????
 */
@SuppressWarnings("unused")
@Slf4j
public class HotLoadPackageEntry {
    private static final String TAG = "HotPluginLoader";

    @SuppressWarnings("unused")
    public static void entry(final Context context, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (StringUtils.equalsIgnoreCase(loadPackageParam.packageName, BuildConfig.APPLICATION_ID)) {
            //CommonUtils.xposedStartSuccess = true;
            Class commonUtilClass = XposedHelpers.findClass("com.virjar.hermes.hermesagent.util.CommonUtils", loadPackageParam.classLoader);
            XposedHelpers.setStaticBooleanField(commonUtilClass, "xposedStartSuccess", true);
            return;
        }
        //??????????????????????????????????????????????????????????????????????????????????????????????????????
        SharedObject.context = context;
        SharedObject.loadPackageParam = loadPackageParam;

        //??????????????????????????????????????????????????????logback???????????????????????????????????????
        Map<String, EmbedWrapper> callbackMap = Maps.newHashMap();
        //??????????????????????????????????????????
        Log.i("weijia", "scan embed wrapper implementation");
        Set<EmbedWrapper> allCallBack = findEmbedCallBack();
        Log.i("weijia", "find :{" + allCallBack.size() + "} embed wrapper");
        for (EmbedWrapper agentCallback : allCallBack) {
            callbackMap.put(agentCallback.targetPackageName(), agentCallback);
        }
        //??????????????????????????????????????????????????????????????????
        Log.i("weijia", "scan external wrapper implementation");
        ExternalWrapper externalCallBack = findExternalCallBack();
        if (externalCallBack != null) {
            AgentCallback old = callbackMap.put(externalCallBack.targetPackageName(), externalCallBack);
            if (old != null) {
                Log.i("weijia", "find a external wrapper and embed wrapper, embed wrapper will replaced by external wrapper");
            }
        }

        if (callbackMap.isEmpty()) {
            return;
        }
        if (callbackMap.size() != 1) {
            Log.e("weijia", "multi wrapper found,hermes agent can only load one hermes wrapper");
            return;
        }
        final EmbedWrapper wrapper = callbackMap.values().iterator().next();
        try {
            if (!wrapper.needHook(loadPackageParam)) {
                return;
            }
            setupInternalComponent();
            final String wrapperName;
            if (wrapper instanceof ExternalWrapper) {
                wrapperName = ((ExternalWrapper) wrapper).wrapperClassName();
            } else {
                wrapperName = wrapper.getClass().getName();
            }
            log.info("????????????: {}", wrapperName);
            //??????????????????
            wrapper.onXposedHotLoad();
            if (wrapper instanceof AgentRegisterAware) {
                log.info("wrapper????????????wrapper?????????????????????????????????wrapper");
                ((AgentRegisterAware) wrapper).setOnAgentReadyListener(new WrapperRegister() {
                    @Override
                    public void regist() {
                        log.info("??????:{} ???hermes server registry", wrapperName);
                        AgentRegister.registerToServer(wrapper, context);
                    }
                });
            } else if (wrapper instanceof ExternalWrapper && ((ExternalWrapper) wrapper).getDelegate() instanceof AgentRegisterAware) {
                ((AgentRegisterAware) ((ExternalWrapper) wrapper).getDelegate()).setOnAgentReadyListener(new WrapperRegister() {
                    @Override
                    public void regist() {
                        log.info("??????:{} ???hermes server registry", wrapperName);
                        AgentRegister.registerToServer(wrapper, context);
                    }
                });
            } else {
                //???agent?????????server?????????server??????rpc???agent
                log.info("??????:{} ???hermes server registry", wrapperName);
                AgentRegister.registerToServer(wrapper, context);
            }
            //??????timer????????????server??????????????????server?????????????????????server
            log.info("?????????server ????????????timer");
            SharedObject.agentTimer.scheduleAtFixedRate(new AgentDaemonTask(context, wrapper), 1000, 4000);
            SharedObject.agentTimer.scheduleAtFixedRate(new AgentCleanExchangeFileTimer(), 2000, 5 * 60 * 1000);
            //???hermesAgent???master?????????????????????????????????????????????????????????hermesAgent???????????????????????????????????????
            //hermesAgent?????????????????????????????????????????????????????????????????????????????????????????????app??????????????????????????????????????????
            exitIfMasterReInstall(SharedObject.context);
        } catch (Exception e) {
            Log.e("weijia", "wrapper:{" + wrapper.targetPackageName() + "} ??????????????????", e);
            AgentRegister.registerToServer(new BrokenWrapper(e), context);
        }
    }

    private static void setupInternalComponent() {
        LogConfigurator.configure(SharedObject.context);
        log.info("plugin startup");

        //?????????????????????classloader
        log.info("setup Xposed Extention component");
        XposedExtensionInstaller.initComponent();
        /*
         * ???????????????????????????????????????????????????????????????????????????????????????app???????????????????????????????????????
         */
        log.info("setup interceptor");
        InvokeInterceptorManager.setUp();

        /*
         * ?????????????????????????????????timer??????????????????????????????????????????
         */
        SharedObject.agentTimer = new Timer("hermesAgentTimer", true);

    }

    private static void exitIfMasterReInstall(Context context) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (StringUtils.isBlank(action)) {
                    return;
                }
                String packageName = CommonUtils.getPackageName(intent);
                if (packageName == null)
                    return;
                if (!StringUtils.equalsIgnoreCase(packageName, BuildConfig.APPLICATION_ID)) {
                    return;
                }
                log.info("master  ?????????????????????slave ??????");

                new Thread("kill-self-thread") {
                    @Override
                    public void run() {
                        //???????????????????????????????????????slave???????????????master?????????dex-cache?????????????????????????????????remove???????????????apk????????????????????????????????????
                        //sleep?????????????????????????????????
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return;
                        }
                        //??????????????????????????????????????????????????????????????????????????????
                        //????????????????????????????????????????????????????????????master???apk?????????????????????????????????????????????slave????????????????????????????????????
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                }.start();

            }


        }, intentFilter);
    }

    @SuppressWarnings("unchecked")
    private static synchronized ExternalWrapper findExternalCallBack() {
        File modulesDir = new File(CommonUtils.HERMES_WRAPPER_DIR);
        if (!modulesDir.exists() || !modulesDir.canRead()) {
            //Log.w("weijia", "hermesModules ????????????????????????HermesWrapper");
            return null;
        }

        Set<EmbedWrapper> ret = Sets.newHashSet();
        for (File apkFilePath : modulesDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.endsWithIgnoreCase(name, ".apk");
            }
        })) {
            //Log.i("weijia", "??????????????????:" + apkFilePath.getAbsolutePath());
            try (ApkFile apkFile = new ApkFile(apkFilePath)) {
                Document androidManifestDocument = CommonUtils.loadDocument(apkFile.getManifestXml());
                NodeList applicationNodeList = androidManifestDocument.getElementsByTagName("application");
                if (applicationNodeList.getLength() == 0) {
                    Log.w("weijia", "the manifest xml file must has application node");
                    continue;
                }
                Element applicationItem = (Element) applicationNodeList.item(0);
                NodeList childNodes = applicationItem.getChildNodes();
                String forTargetPackageName = null;
                for (int i = 0; i < childNodes.getLength(); i++) {
                    Node item = childNodes.item(i);
                    if (!(item instanceof Element)) {
                        continue;
                    }
                    Element metaItem = (Element) item;
                    if (!StringUtils.equals(metaItem.getTagName(), "meta-data")) {
                        continue;
                    }
                    if (!StringUtils.equals(metaItem.getAttribute("android:name"), APICommonUtils.HERMES_EXTERNAL_WRAPPER_FLAG_KEY)) {
                        continue;
                    }
                    forTargetPackageName = metaItem.getAttribute("android:value");
                    break;
                }
                if (StringUtils.isBlank(forTargetPackageName)) {
                    Log.w("weijia", "can not find hermes external wrapper target package config,please config it application->meta-data node");
                    continue;
                }
                if (!StringUtils.equals(forTargetPackageName, SharedObject.loadPackageParam.packageName)) {
                    // Log.i("weijia", "?????????match??????????????????" + SharedObject.loadPackageParam.packageName + " wrapper???????????????" + forTargetPackageName);
                    continue;
                }

                String packageName = apkFile.getApkMeta().getPackageName();
                ClassScanner.SubClassVisitor<AgentCallback> subClassVisitor = new ClassScanner.SubClassVisitor(true, AgentCallback.class);
                ClassScanner.scan(subClassVisitor, Sets.newHashSet(packageName), apkFilePath);
                if (subClassVisitor.getSubClass().isEmpty()) {
                    Log.w("weijia", "can not find any hermes wrapper implement in apk file :" + apkFilePath.getAbsoluteFile());
                    continue;
                }
                if (subClassVisitor.getSubClass().size() > 1) {
                    Log.e("weijia", "a wrapper apk module can hold only one hermes wrapper ,now : " + StringUtils.join(Iterables.transform(subClassVisitor.getSubClass(), new Function<Class<? extends AgentCallback>, String>() {
                        @Override
                        public String apply(Class<? extends AgentCallback> input) {
                            return input.getName();
                        }
                    }), ","));
                    continue;
                }
                AgentCallback agentCallback = subClassVisitor.getSubClass().get(0).newInstance();
                if (agentCallback instanceof MultiActionWrapper) {
                    log.info("weijia", "multiAction wrapper found,now scan action request handler");
                    MultiActionWrapper multiActionWrapper = (MultiActionWrapper) agentCallback;
                    ArrayList<ActionRequestHandler> requestHandlers = MultiActionWrapperFactory.scanActionWrappers(packageName, apkFilePath, agentCallback.getClass().getClassLoader());
                    if (requestHandlers.size() == 0) {
                        Log.e("weijia", "can not find any action request implement in ak file:" + apkFilePath.getAbsoluteFile());
                        return null;
                    }
                    for (ActionRequestHandler actionRequestHandler : requestHandlers) {
                        try {
                            multiActionWrapper.registryHandler(actionRequestHandler);
                        } catch (Exception e) {
                            //ignore
                            log.warn("register handler:{} failed", actionRequestHandler, e);
                        }
                    }
                }
                return new ExternalWrapper(agentCallback, SharedObject.loadPackageParam.packageName, apkFile.getApkMeta().getVersionCode());
            } catch (Exception e) {
                Log.e("weijia", "failed to load hermes-wrapper module", e);
            }
        }
        return null;
    }

    private static List<EmbedWrapper> transform(List<Class<? extends
            AgentCallback>> classList, final long versionCode) {
        return Lists.newArrayList(Iterables.filter(Iterables.transform(classList, new Function<Class<? extends AgentCallback>, EmbedWrapper>() {
            @Nullable
            @Override
            public EmbedWrapper apply(@Nullable Class<? extends AgentCallback> input) {
                if (input == null) {
                    return null;
                }
                final AgentCallback agentCallback;
                try {
                    agentCallback = input.newInstance();
                } catch (Exception e) {
                    Log.w("weijia", "failed to load create plugin", e);
                    return null;
                }
                return new ExternalWrapper(agentCallback, SharedObject.loadPackageParam.packageName, versionCode);
            }
        }), new Predicate<EmbedWrapper>() {
            @Override
            public boolean apply(@Nullable EmbedWrapper input) {
                return input != null;
            }
        }));
    }

    private static Set<EmbedWrapper> filter(List<Class<? extends
            EmbedWrapper>> subClassVisitor) {
        return Sets.newHashSet(Iterables.filter(Lists.transform(subClassVisitor, new Function<Class<? extends AgentCallback>, EmbedWrapper>() {
            @Nullable
            @Override
            public EmbedWrapper apply(Class<? extends AgentCallback> input) {
                try {
                    final AgentCallback agentCallback = input.newInstance();
                    if (agentCallback instanceof EmbedWrapper) {
                        return (EmbedWrapper) agentCallback;
                    }

                } catch (InstantiationException | IllegalAccessException e) {
                    Log.w("weijia", "failed to load create plugin", e);
                }
                return null;
            }
        }), new Predicate<EmbedWrapper>() {
            @Override
            public boolean apply(@Nullable EmbedWrapper input) {
                return input != null
                        && input.needHook(SharedObject.loadPackageParam)
                        && StringUtils.equalsIgnoreCase(input.targetPackageName(), SharedObject.loadPackageParam.packageName);
            }
        }));
    }

    /**
     * ???HermesAgent????????????HermesWrapper??????
     */
    @SuppressWarnings("unchecked")
    private static Set<EmbedWrapper> findEmbedCallBack() {
        ClassScanner.SubClassVisitor<EmbedWrapper> subClassVisitor = new ClassScanner.SubClassVisitor(true, EmbedWrapper.class);
        ClassScanner.scan(subClassVisitor, Constant.appHookSupperPackage);
        List<Class<? extends EmbedWrapper>> subClass = subClassVisitor.getSubClass();
        return filter(subClass);
    }
}
