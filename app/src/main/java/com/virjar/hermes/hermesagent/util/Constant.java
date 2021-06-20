package com.virjar.hermes.hermesagent.util;

/**
 * Created by virjar on 2018/8/23.
 */

public interface Constant {
    int httpServerPort = 5597;
    String httpServerPingPath = "/ping";
    String startAppPath = "/startApp";
    String jsonContentType = "application/json; charset=utf-8";

    int status_ok = 0;
    int status_failed = -1;
}
