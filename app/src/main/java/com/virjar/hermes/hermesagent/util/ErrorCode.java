package com.virjar.hermes.hermesagent.util;

import com.google.common.collect.Maps;
import com.virjar.hermes.hermesagent.hermes_api.Constant;

import java.util.Map;


/**
 * Created by virjar on 2018/8/24.
 */
//TODO
public class ErrorCode {
    private static Map<Integer, String> codes = Maps.newHashMap();

    private static void initCodes() {
        codes.put(Constant.status_service_not_available, Constant.serviceNotAvailableMessage);
    }

}
