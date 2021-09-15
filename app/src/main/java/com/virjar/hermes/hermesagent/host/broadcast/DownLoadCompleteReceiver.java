package com.virjar.hermes.hermesagent.host.broadcast;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.virjar.hermes.hermesagent.host.manager.TargetAppInstallTaskQueue;

/**
 * Created by virjar on 2018/9/8.
 */

public class DownLoadCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            TargetAppInstallTaskQueue.getInstance().onFileDownloadSuccess(id, context);
        } else if (intent.getAction().equals(DownloadManager.ACTION_NOTIFICATION_CLICKED)) {
            Toast.makeText(context, "别瞎点！！！", Toast.LENGTH_SHORT).show();
        }
    }
}