package com.virjar.hermes.hermesagent.host.manager;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.host.orm.ServiceModel;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.SUShell;

import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/9/8.
 */
@Slf4j
public class InstallTaskQueue {
    private static InstallTaskQueue instance = new InstallTaskQueue();
    private Set<String> doingSet = Sets.newConcurrentHashSet();
    private Map<Long, ServiceModel> doingTasks = Maps.newConcurrentMap();

    public static InstallTaskQueue getInstance() {
        return instance;
    }

    private File findTargetApk(Context context, final ServiceModel targetAppModel) {
        File dir = new File(context.getFilesDir(), "agentApk");
        try {
            FileUtils.forceMkdir(dir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        File[] candidateApks = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.endsWithIgnoreCase(name, ".apk") && StringUtils.contains(name, targetAppModel.getTargetAppPackage());
            }
        });
        if (candidateApks == null) {
            return null;
        }

        for (File file : candidateApks) {
            ApkMeta apkMeta = CommonUtils.getAPKMeta(file);
            if (apkMeta == null) {
                continue;
            }
            if (StringUtils.equals(apkMeta.getPackageName(), targetAppModel.getTargetAppPackage())
                    && apkMeta.getVersionCode().equals(targetAppModel.getTargetAppVersionCode())) {
                return file;
            }
        }
        return null;
    }

    private File findWrapper(final ServiceModel serviceModel) {
        File wrapperDir = new File(CommonUtils.HERMES_WRAPPER_DIR);
        if (!wrapperDir.exists()) {
            return null;
        }
        File[] files = wrapperDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.endsWithIgnoreCase(name, ".apk") && StringUtils.contains(name, serviceModel.getWrapperPackage());
            }
        });

        if (files == null) {
            return null;
        }

        for (File file : files) {
            ApkMeta apkMeta = CommonUtils.getAPKMeta(file);
            if (apkMeta == null) {
                continue;
            }
            if (StringUtils.equals(apkMeta.getPackageName(), serviceModel.getWrapperPackage())) {
                if (apkMeta.getVersionCode().equals(serviceModel.getWrapperVersionCode())) {
                    return file;
                } else {
                    //由于其他app跨进程查询HermesAgent的db比较麻烦，所以这里直接非当前版本的文件
                    FileUtils.deleteQuietly(file);
                }
            }
        }
        return null;
    }


    /**
     * install a wrapper
     *
     * @param serviceModel task model
     * @param context      the android context
     * @return true if the wrapper exist already
     */
    public boolean installWrapper(final ServiceModel serviceModel, Context context) {
        log.info("get a wrapper install task:{}", serviceModel.getTargetAppPackage());
        File wrapper = findWrapper(serviceModel);
        if (wrapper != null) {
            log.info("find a suitable wrapper apk file,skip to install it");
            return false;
        }
        synchronized (this) {
            if (doingSet.contains(serviceModel.getWrapperPackage())) {
                return true;
            }
            doingSet.add(serviceModel.getWrapperPackage());
        }
        log.info("download wrapper apk from url:{}", serviceModel.getWrapperAppDownloadUrl());
        //download
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(serviceModel.getWrapperAppDownloadUrl()));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setTitle("hermesAgent自动下载");
        request.setDescription(serviceModel.getWrapperPackage() + "正在下载");
        request.setAllowedOverRoaming(false);
        //设置文件存放目录
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "hermes_apk");
        DownloadManager downManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downManager == null) {
            throw new IllegalStateException("can not find system service : DOWNLOAD_SERVICE");
        }
        long id = downManager.enqueue(request);
        doingTasks.put(id, serviceModel);
        log.info("download task enqueued,task id:{}", id);
        return true;
    }

    public void installTargetApk(final ServiceModel serviceModel, Context context) {
        log.info("get a target apk install task:{}", serviceModel.getTargetAppPackage());
        //文件扫描，寻找满足条件的apk文件
        File targetApk = findTargetApk(context, serviceModel);
        if (targetApk != null) {
            log.info("find a suitable target apk file， now install it");
            installTargetApk(targetApk);
            return;
        }

        synchronized (this) {
            if (doingSet.contains(serviceModel.getTargetAppPackage())) {
                return;
            }
            doingSet.add(serviceModel.getTargetAppPackage());
        }
        log.info("download target apk from url:{}", serviceModel.getTargetAppDownloadUrl());
        //download
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(serviceModel.getTargetAppDownloadUrl()));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setTitle("hermesAgent自动下载");
        request.setDescription(serviceModel.getTargetAppPackage() + "正在下载");
        request.setAllowedOverRoaming(false);
        //设置文件存放目录
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "hermes_apk");
        DownloadManager downManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downManager == null) {
            throw new IllegalStateException("can not find system service : DOWNLOAD_SERVICE");
        }
        long id = downManager.enqueue(request);
        doingTasks.put(id, serviceModel);
        log.info("download task enqueued,task id:{}", id);
    }


    public void onFileDownloadSuccess(long id, Context context) {
        DownloadManager downManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downManager == null) {
            throw new IllegalStateException("can not find system service : DOWNLOAD_SERVICE");
        }
        String localFileName = null;
        ServiceModel downloadTaskMode = doingTasks.remove(id);
        if (downloadTaskMode == null) {
            log.warn("can not find download task model");
            return;
        }
        log.info("download:{} success", downloadTaskMode.getTargetAppPackage());
        doingSet.remove(downloadTaskMode.getTargetAppPackage());
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        try (Cursor cursor = downManager.query(query)) {
            while (cursor.moveToNext()) {
                localFileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_FAILED) {
                    log.warn("{} download failed ", downloadTaskMode.getTargetAppPackage());
                    Toast.makeText(context, downloadTaskMode.getTargetAppPackage() + "download failed ", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        if (localFileName == null) {
            Toast.makeText(context, downloadTaskMode.getTargetAppPackage() + "download failed ,system error", Toast.LENGTH_SHORT).show();
            return;
        }
        log.info("download file path:{}", localFileName);
        File dir = new File(context.getFilesDir(), "agentApk");
        ApkMeta apkMeta = CommonUtils.getAPKMeta(new File(localFileName));
        if (apkMeta == null) {
            //文件损坏，下载不正确
            return;
        }
        if (StringUtils.equals(apkMeta.getPackageName(), downloadTaskMode.getWrapperPackage())) {
            //下载的wrapper，wrapper不需要安装，移动到对wrapper目录文件夹下面即可
            try {
                File wrapperPath = new File(CommonUtils.HERMES_WRAPPER_DIR, "hermes_wrapper_app_" +
                        apkMeta.getPackageName() + "_" + apkMeta.getVersionName() + "_" + apkMeta.getVersionCode() + ".apk");
                log.info("download wrapper success , now install it to path:{}", wrapperPath.getAbsoluteFile());
                FileUtils.moveFile(new File(localFileName), wrapperPath);
                log.info("grant access privilege for target app..");
                //需要设置为可读写，否则其他app无法
                SUShell.run("chmod 777 " + wrapperPath.getParentFile().getAbsolutePath());
                SUShell.run("chmod 777 " + wrapperPath.getAbsolutePath());
                log.info("wrapper install succe ss");
            } catch (IOException e) {
                log.error("install wrapper failed", e);
                //TODO eat it now
            }
            return;
        }
        //下载的是targetAPP，targetApp需要安装
        try {
            FileUtils.moveFile(new File(localFileName), new File(dir,
                    "hermes_target_app_" + apkMeta.getPackageName()
                            + "_" + apkMeta.getVersionName() + "_" + apkMeta.getVersionCode()
                            + ".apk"));
        } catch (IOException e) {
            log.error("move file failed", e);
            //如果发生了IO异常，那么直接安装，可能阻塞主线程
            installTargetApk(new File(localFileName));
        }
    }


    private void installTargetApk(File apkFile) {
        //目前各种手段都无法静默越权安装，所以我们这里直接su了，反而简单一些
        log.info("install apk file:{}", apkFile.getAbsoluteFile());
        File tempFile = new File("/data/local/tmp", apkFile.getName());
        SUShell.run("cp " + apkFile.getAbsolutePath() + "  " + tempFile.getAbsolutePath());
        SUShell.run("chmod 777 " + tempFile.getAbsolutePath());
        SUShell.run("pm install -r " + tempFile.getAbsolutePath());
        FileUtils.deleteQuietly(tempFile);
        log.info("apk install success");
    }
}
