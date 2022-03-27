package com.virjar.hermes.hermesagent.util;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/9/23.
 */
@Slf4j
public class SUShell extends Shell.SU {

    /**
     * Runs command as root (if available) and return output
     *
     * @param command The command to run
     * @return Output of the command, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(String command) {

        List<String> ret = Shell.run("su", new String[]{
                command
        }, null, true);
        log.info("execute su command:{} execute result:", command, JSONObject.toJSONString(ret
        ));
        return ret;
    }

    /**
     * Runs commands as root (if available) and return output
     *
     * @param commands The commands to run
     * @return Output of the commands, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(List<String> commands) {
        List<String> ret = Shell.run("su", commands.toArray(new String[commands.size()]), null, true);
        log.info("execute su command:{} execute result:", JSONObject.toJSONString(commands), JSONObject.toJSONString(ret));
        return ret;
    }

    /**
     * Runs commands as root (if available) and return output
     *
     * @param commands The commands to run
     * @return Output of the commands, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(String[] commands) {
        List<String> ret = Shell.run("su", commands, null, true);
        log.info("execute su command:{} execute result:", JSONObject.toJSONString(commands), JSONObject.toJSONString(ret));
        return ret;
    }
}
