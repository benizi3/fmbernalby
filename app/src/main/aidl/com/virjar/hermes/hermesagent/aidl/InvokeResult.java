package com.virjar.hermes.hermesagent.aidl;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by virjar on 2018/8/22.
 */

public class InvokeResult implements Parcelable {
    public static final int dataTypeString = 0;
    public static final int dataTypeJson = 1;

    public static final int statusOK = 0;
    public static final int statusFailed = -1;

    private int status;
    private int dataType;
    private String theData;
    private boolean useFile;

    private static final String TAG = "BinderRPC";

    public InvokeResult(Parcel in) {
        status = in.readInt();
        dataType = in.readInt();
        theData = in.readString();
        useFile = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status);
        dest.writeInt(dataType);
        dest.writeString(theData);
        dest.writeByte((byte) (useFile ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InvokeResult> CREATOR = new Creator<InvokeResult>() {
        @Override
        public InvokeResult createFromParcel(Parcel in) {
            return new InvokeResult(in);
        }

        @Override
        public InvokeResult[] newArray(int size) {
            return new InvokeResult[size];
        }
    };

    @SuppressLint("SetWorldWritable")
    public static InvokeResult success(String body) {
        boolean useFile = body.length() > 4096;
        if (!useFile) {
            return new InvokeResult(statusOK, dataTypeString, body, false);
        }
        File file = CommonUtils.genTempFile();
        try {
            if (!file.createNewFile()) {
                if (!file.setWritable(true, false)) {
                    return failed("change temp file permisson failed " + file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            return failed("failed to create a temp file " + file.getAbsolutePath());
        }

        try {
            BufferedWriter bufferedWriter = Files.newWriter(file, Charsets.UTF_8);
            bufferedWriter.write(body);
            bufferedWriter.close();
            return new InvokeResult(statusOK, dataTypeString, file.getAbsolutePath(), true);
        } catch (IOException e) {
            return failed("failed to save data to file" + e.getMessage());
        }
    }

    @SuppressLint("SetWorldWritable")
    public static InvokeResult success(JSONObject jsonObject) {
        String body = jsonObject.toJSONString();
        boolean useFile = body.length() > 4096;
        if (!useFile) {
            return new InvokeResult(statusOK, dataTypeJson, body, false);
        }
        File file = CommonUtils.genTempFile();
        try {
            if (!file.createNewFile()) {
                if (!file.setWritable(true, false)) {
                    return failed("change temp file permisson failed " + file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            return failed("failed to create a temp file " + file.getAbsolutePath());
        }

        try {
            BufferedWriter bufferedWriter = Files.newWriter(file, Charsets.UTF_8);
            bufferedWriter.write(body);
            bufferedWriter.close();
            return new InvokeResult(statusOK, dataTypeJson, file.getAbsolutePath(), true);
        } catch (IOException e) {
            return failed("failed to save data to file" + e.getMessage());
        }

    }

    public static InvokeResult failed(String message) {
        if (message.length() > 4096) {
            message = message.substring(0, 4096);
        }
        return new InvokeResult(statusFailed, dataTypeString, message, false);
    }


    public InvokeResult(int status, int dataType, String theData, boolean useFile) {
        this.status = status;
        this.dataType = dataType;
        this.theData = theData;
        this.useFile = useFile;
    }

    public int getStatus() {
        return status;
    }

    public int getDataType() {
        return dataType;
    }

    public String getTheData() {
        if (!useFile) {
            return theData;
        }
        File file = new File(theData);
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            throw new IllegalStateException("target file " + file.getAbsolutePath() + " can not be accessed");
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            String ret = IOUtils.toString(fileInputStream, Charsets.UTF_8);
            fileInputStream.close();
            if (!file.delete()) {
                Log.w(TAG, "delete binder file failed:" + file.getAbsolutePath());
            }
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
