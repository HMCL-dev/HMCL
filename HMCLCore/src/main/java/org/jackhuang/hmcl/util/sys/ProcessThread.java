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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.api.event.EventHandler;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.util.code.Charsets;
import org.jackhuang.hmcl.api.IProcess;

/**
 *
 * @author huangyuhui
 */
public class ProcessThread extends Thread {

    ProcessMonitor monitor;
    boolean readError;
    public final EventHandler<SimpleEvent<String>> printlnEvent = new EventHandler<>();
    public final EventHandler<SimpleEvent<IProcess>> stopEvent = new EventHandler<>();

    public ProcessThread(ProcessMonitor monitor, boolean readError) {
        this.monitor = monitor;
        this.readError = readError;
        setDaemon(readError);
    }

    public IProcess getProcess() {
        return monitor.getProcess();
    }

    @Override
    public void run() {
        setName("ProcessMonitor");
        BufferedReader br = null;
        IProcess p = monitor.getProcess();
        try {
            InputStream in = readError ? p.getRawProcess().getErrorStream() : p.getRawProcess().getInputStream();
            br = new BufferedReader(new InputStreamReader(in, Charsets.toCharset()));

            String line;
            while (p.isRunning())
                while ((line = br.readLine()) != null) {
                    printlnEvent.fire(new SimpleEvent<>(monitor, line));
                    System.out.println("MC: " + line);
                    p.getStdOutLines().add(line);
                }
            while ((line = br.readLine()) != null) {
                printlnEvent.fire(new SimpleEvent<>(monitor, line));
                System.out.println("MC: " + line);
                p.getStdOutLines().add(line);
            }
            stopEvent.fire(new SimpleEvent<>(this, p));
        } catch (IOException e) {
            HMCLog.err("An error occured when reading process stdout/stderr.", e);
        } finally {
            IOUtils.closeQuietly(br);
        }
    }
}
