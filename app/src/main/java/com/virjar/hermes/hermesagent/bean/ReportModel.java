package com.virjar.hermes.hermesagent.bean;

import java.util.Collection;

/**
 * Created by virjar on 2018/8/24.
 */

public class ReportModel {
    private String agentServerIP;
    private int agentServerPort;
    private double cpuLoader;
    private double memoryInfo;
    private String mac;
    private String brand;
    private String systemVersion;

    private Collection<String> onlineServices;

    public String getAgentServerIP() {
        return agentServerIP;
    }

    public void setAgentServerIP(String agentServerIP) {
        this.agentServerIP = agentServerIP;
    }

    public int getAgentServerPort() {
        return agentServerPort;
    }

    public void setAgentServerPort(int agentServerPort) {
        this.agentServerPort = agentServerPort;
    }

    public double getCpuLoader() {
        return cpuLoader;
    }

    public void setCpuLoader(double cpuLoader) {
        this.cpuLoader = cpuLoader;
    }

    public double getMemoryInfo() {
        return memoryInfo;
    }

    public void setMemoryInfo(double memoryInfo) {
        this.memoryInfo = memoryInfo;
    }

    public Collection<String> getOnlineServices() {
        return onlineServices;
    }

    public void setOnlineServices(Collection<String> onlineServices) {
        this.onlineServices = onlineServices;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }
}
