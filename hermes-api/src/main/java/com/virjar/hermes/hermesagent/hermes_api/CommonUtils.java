package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by virjar on 2018/9/13.
 */

public class CommonUtils {
    private static AtomicLong fileSequence = new AtomicLong(1);

    public static File genTempFile(Context context) {
        File cacheDir = context.getCacheDir();
        File retFile = new File(cacheDir, "hermes_exchange_" + System.currentTimeMillis()
                + "_" + fileSequence.incrementAndGet()
                + "_" + Thread.currentThread().getId());
        try {
            if (!retFile.createNewFile()) {
                throw new IllegalStateException("failed to create temp file :" + retFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        makeFileRW(retFile);
        return retFile;
    }

    public static void makeFileRW(File file) {
        try {
            int returnCode = Runtime.getRuntime().exec("chmod 666 " + file.getAbsolutePath()).waitFor();
            if (returnCode != 0) {
                throw new IllegalStateException("failed to change temp file mode");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
