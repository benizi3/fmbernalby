package com.virjar.hermes.hermesagent.bean;

import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

/**
 * Created by virjar on 2018/8/23.
 */

public class CommonRes {
    private int status;
    private String errorMessage;
    private Object data;


    public static CommonRes success(Object data) {
        return new CommonRes(Constant.status_ok, null, data);
    }

    public static CommonRes failed(int status
            , String errorMessage) {
        return new CommonRes(status, errorMessage, null);
    }

    public static CommonRes failed(String errorMessage) {
        return failed(Constant.status_failed, errorMessage);
    }

    public static CommonRes failed(Exception e) {
        return failed(CommonUtils.translateSimpleExceptionMessage(e));
    }

    public CommonRes(int status, String errorMessage, Object data) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.data = data;
    }

    public int getStatus() {
        return status;
    }


    public String getErrorMessage() {
        return errorMessage;
    }


    public Object getData() {
        return data;
    }

}
