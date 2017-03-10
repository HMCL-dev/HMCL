/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util.sys;

import org.jackhuang.hmcl.api.IProcess;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import org.jackhuang.hmcl.api.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class JavaProcess implements IProcess {

    private final CountDownLatch latch = new CountDownLatch(2);
    private final List<String> commands;
    private final Process process;
    private final Vector<String> stdOutLines = new Vector<>();

    public JavaProcess(List<String> commands, Process process) {
        this.commands = commands;
        this.process = process;
    }

    public JavaProcess(String[] commands, Process process) {
        this(Arrays.asList(commands), process);
    }

    @Override
    public Process getRawProcess() {
        return this.process;
    }

    @Override
    public List<String> getStartupCommands() {
        return this.commands;
    }

    @Override
    public String getStartupCommand() {
        return this.process.toString();
    }

    @Override
    public List<String> getStdOutLines() {
        return this.stdOutLines;
    }

    @Override
    public boolean isRunning() {
        try {
            this.process.exitValue();
        } catch (IllegalThreadStateException ex) {
            return true;
        }

        return false;
    }

    CountDownLatch getLatch() {
        return latch;
    }

    @Override
    public void waitForCommandLineCompletion() {
        try {
            latch.await();
        } catch (InterruptedException ignore) {
            HMCLog.warn("Thread has been interrupted.", ignore);
        }
    }

    @Override
    public int getExitCode() {
        try {
            return this.process.exitValue();
        } catch (IllegalThreadStateException ex) {
            ex.fillInStackTrace();
            throw ex;
        }
    }

    @Override
    public String toString() {
        return "JavaProcess[commands=" + this.commands + ", isRunning=" + isRunning() + "]";
    }

    @Override
    public void stop() {
        this.process.destroy();
    }
}
