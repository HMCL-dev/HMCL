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
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.api.event.EventHandler;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.util.code.Charsets;
import org.jackhuang.hmcl.api.IProcess;

/**
 * Watch the process command line output(stdout or stderr).
 *
 * @author huangyuhui
 */
public class ProcessThread extends Thread {

    List<String> lines = new ArrayList<>();
    ProcessMonitor monitor;
    IProcess p;
    boolean readError;
    public final EventHandler<PrintlnEvent> printlnEvent = new EventHandler<>();
    public final EventHandler<SimpleEvent<IProcess>> stopEvent = new EventHandler<>();

    public ProcessThread(ProcessMonitor monitor, boolean readError) {
        this.monitor = monitor;
        this.readError = readError;
        p = monitor.getProcess();
        setDaemon(readError);
    }

    public IProcess getProcess() {
        return p;
    }

    /**
     * Only get stdout or stderr output according to readError().
     * Invoke this method only if the process thread has stopped.
     */
    public List<String> getLines() {
        return lines;
    }

    @Override
    public void run() {
        setName("ProcessMonitor");
        BufferedReader br = null;
        try {
            InputStream in = readError ? p.getRawProcess().getErrorStream() : p.getRawProcess().getInputStream();
            br = new BufferedReader(new InputStreamReader(in, Charsets.toCharset()));

            String line;
            while (p.isRunning())
                while ((line = br.readLine()) != null)
                    println(line);
            while ((line = br.readLine()) != null)
                println(line);
        } catch (IOException e) {
            HMCLog.err("An error occured when reading process stdout/stderr.", e);
        } finally {
            IOUtils.closeQuietly(br);
        }
        stopEvent.fire(new SimpleEvent<>(this, p));
    }

    protected void println(String line) {
        printlnEvent.fire(new PrintlnEvent(monitor, line, readError));
        (readError ? System.err : System.out).println(line);
        lines.add(line);
        p.getStdOutLines().add(line);
    }
}
