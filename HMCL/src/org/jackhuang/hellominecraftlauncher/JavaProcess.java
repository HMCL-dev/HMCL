/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.util.List;
import org.jackhuang.hellominecraftlauncher.utilities.LimitedCapacityList;

/**
 *
 * @author hyh
 */
public class JavaProcess {

    private final List<String> commands;
    private final Process process;
    private final LimitedCapacityList<String> sysOutLines = new LimitedCapacityList<String>(String.class, 5);

    public JavaProcess(List<String> commands, Process process) {
        this.commands = commands;
        this.process = process;
    }

    public Process getRawProcess() {
        return this.process;
    }

    public List<String> getStartupCommands() {
        return this.commands;
    }

    public String getStartupCommand() {
        return this.process.toString();
    }

    public LimitedCapacityList<String> getSysOutLines() {
        return this.sysOutLines;
    }

    public boolean isRunning() {
        try {
            this.process.exitValue();
        } catch (IllegalThreadStateException ex) {
            return true;
        }

        return false;
    }

    public int getExitCode() {
        try {
            return this.process.exitValue();
        } catch (IllegalThreadStateException ex) {
            ex.fillInStackTrace();
            throw ex;
        }
    }

    public String toString() {
        return "JavaProcess[commands=" + this.commands + ", isRunning=" + isRunning() + "]";
    }

    public void stop() {
        this.process.destroy();
    }
}