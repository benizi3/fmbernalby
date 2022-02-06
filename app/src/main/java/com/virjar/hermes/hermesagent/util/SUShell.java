package com.virjar.hermes.hermesagent.util;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by virjar on 2018/9/23.
 */

public class SUShell extends Shell.SU {

    /**
     * Runs command as root (if available) and return output
     *
     * @param command The command to run
     * @return Output of the command, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(String command) {
        return Shell.run("su", new String[]{
                command
        }, null, true);
    }

    /**
     * Runs commands as root (if available) and return output
     *
     * @param commands The commands to run
     * @return Output of the commands, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(List<String> commands) {
        return Shell.run("su", commands.toArray(new String[commands.size()]), null, true);
    }

    /**
     * Runs commands as root (if available) and return output
     *
     * @param commands The commands to run
     * @return Output of the commands, or null if root isn't available or in
     * case of an error
     */
    public static List<String> run(String[] commands) {
        return Shell.run("su", commands, null, true);
    }
}
