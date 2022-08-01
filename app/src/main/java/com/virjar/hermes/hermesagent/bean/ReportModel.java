package com.virjar.hermes.hermesagent.bean;

import java.util.Collection;

import lombok.Data;

/**
 * Created by virjar on 2018/8/24.<br>
 * the model report to hermes admin<br>
 * update on 2018/12/04
 */
@Data
public class ReportModel {
    private String agentServerIP;
    private int agentServerPort;
    private double cpuLoader;
    private double memoryInfo;
    private String mac;
    private String brand;
    private String systemVersion;

    private Collection<String> onlineServices;
}
