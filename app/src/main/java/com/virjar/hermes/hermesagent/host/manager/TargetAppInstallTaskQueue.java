package com.virjar.hermes.hermesagent.host.manager;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.BuildConfig;
import com.virjar.hermes.hermesagent.util.Constant;
import com.virjar.hermes.hermesagent.util.URLEncodeUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.Set;

/**
 * Created by virjar on 2018/9/8.
 */

public class TargetAppInstallTaskQueue {
    private static TargetAppInstallTaskQueue instance = new TargetAppInstallTaskQueue();
    private Set<String> doingSet = Sets.newConcurrentHashSet();
    private Map<Long, DownloadTaskMode> doingTasks = Maps.newConcurrentMap();

    public static TargetAppInstallTaskQueue getInstance() {
        return instance;
    }

    public void install(final String targetAppPackage, Context context) {
        if (BuildConfig.DEBUG) {
            return;
        }
        File dir = new File(context.getFilesDir(), "agentApk");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] candidateApks = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.startsWithIgnoreCase(name, targetAppPackage + "_")
                        && StringUtils.endsWithIgnoreCase(name, ".apk");
            }
        });
        if (candidateApks.length > 1) {
            for (File file : candidateApks) {
                file.delete();
            }
        } else if (candidateApks.length == 1) {
            //install it
            File apkFile = candidateApks[0];
            install(context, apkFile);
            return;
        }
        if (doingSet.contains(targetAppPackage)) {
            return;
        }
        doingSet.add(targetAppPackage);
        //download
        String url = Constant.serverBaseURL + Constant.downloadPath + "?package=" + URLEncodeUtil.escape(targetAppPackage);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setTitle("hermesAgent自动下载");
        request.setDescription(targetAppPackage + "正在下载");
        request.setAllowedOverRoaming(false);
        //设置文件存放目录
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "hermes_apk");
        DownloadManager downManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downManager == null) {
            throw new IllegalStateException("can not find system service : DOWNLOAD_SERVICE");
        }
        long id = downManager.enqueue(request);
        DownloadTaskMode downloadTaskMode = new DownloadTaskMode();
        downloadTaskMode.id = id;
        downloadTaskMode.packageName = targetAppPackage;
        doingTasks.put(id, downloadTaskMode);

    }


    public void onFileDownloadSuccess(long id, Context context) {
        DownloadManager downManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downManager == null) {
            throw new IllegalStateException("can not find system service : DOWNLOAD_SERVICE");
        }
        String localFileName = null;
        DownloadTaskMode downloadTaskMode = doingTasks.get(id);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        try (Cursor cursor = downManager.query(query)) {
            while (cursor.moveToNext()) {
                localFileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_FAILED) {
                    Toast.makeText(context, downloadTaskMode.packageName + "download failed ", Toast.LENGTH_SHORT).show();
                    doingTasks.remove(id);
                    doingSet.remove(downloadTaskMode.packageName);
                    return;
                }
            }
        }
        if (localFileName == null) {
            Toast.makeText(context, downloadTaskMode.packageName + "download failed ,system error", Toast.LENGTH_SHORT).show();
            return;
        }

        File dir = new File(context.getFilesDir(), "agentApk");
        File saveFile = new File(dir, downloadTaskMode.packageName + "_.apk");
        if (!new File(localFileName).renameTo(saveFile)) {
            throw new IllegalStateException("downloader rename apk file failed, from" + localFileName + " to :" + saveFile.getAbsolutePath());
        }
        install(downloadTaskMode.packageName, context);
    }

    private class DownloadTaskMode {
        private long id;
        private String packageName;
    }

    private void install(Context context, File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(
                    context
                    , "com.virjar.hermes.hermesagent.fileprovider"
                    , apkFile);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        context.startActivity(intent);
    }
}
