package com.virjar.hermes.hermesagent.hermes_api;

/**
 * Created by virjar on 2018/8/23.
 */

public class CommonRes {
    private int status;
    private String errorMessage;
    private Object data;
    private String requestId;


    public static CommonRes success(Object data) {
        return new CommonRes(Constant.status_ok, null, data, null);
    }

    public static CommonRes failed(int status
            , String errorMessage) {
        return new CommonRes(status, errorMessage, null, null);
    }

    public static CommonRes failed(int status
            , String errorMessage, String requestId) {
        return new CommonRes(status, errorMessage, null, requestId);
    }

    public static CommonRes failed(String errorMessage) {
        return failed(Constant.status_failed, errorMessage);
    }

    public static CommonRes failed(Exception e) {
        return failed(APICommonUtils.translateSimpleExceptionMessage(e));
    }

    public CommonRes(int status, String errorMessage, Object data, String requestId) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.data = data;
        this.requestId = requestId;
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

    public String getRequestId() {
        return requestId;
    }
}
