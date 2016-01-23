/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.utils.system;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import org.jackhuang.hellominecraft.utils.logging.HMCLog;
import org.jackhuang.hellominecraft.utils.EventHandler;

/**
 *
 * @author huangyuhui
 */
public class ProcessThread extends Thread {

    JavaProcess p;
    boolean readError = false, enableReading = true;

    public final EventHandler<String> printlnEvent = new EventHandler<>(this);
    public final EventHandler<JavaProcess> stopEvent = new EventHandler<>(this);

    public ProcessThread(JavaProcess process, boolean readError, boolean enableReading) {
        p = process;
        this.readError = readError;
        this.enableReading = enableReading;
    }

    public JavaProcess getProcess() {
        return p;
    }

    @Override
    public void run() {
        try {
            InputStreamReader br;
            if (enableReading) {
                InputStream in = readError ? p.getRawProcess().getErrorStream() : p.getRawProcess().getInputStream();
                try {
                    br = new InputStreamReader(in, System.getProperty("sun.jnu.encoding", "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    HMCLog.warn("Unsupported encoding: " + System.getProperty("sun.jnu.encoding", "UTF-8"), ex);
                    br = new InputStreamReader(in);
                }
            } else
                br = null;

            int ch;
            String line = "";
            while (p.isRunning())
                if (br != null)
                    while ((ch = br.read()) != -1)
                        if (ch == '\n') {
                            printlnEvent.execute(line);
                            if (readError) {
                                System.err.println(line);
                                p.getStdErrLines().add(line);
                            } else {
                                System.out.println(line);
                                p.getStdOutLines().add(line);
                            }
                            line = "";
                        } else
                            line += (char) ch;
                else
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {
                    }
            if (br != null)
                while ((ch = br.read()) != -1)
                    if (ch == '\n') {
                        printlnEvent.execute(line);
                        if (readError) {
                            System.err.println(line);
                            p.getStdErrLines().add(line);
                        } else {
                            System.out.println(line);
                            p.getStdOutLines().add(line);
                        }
                        line = "";
                    } else
                        line += (char) ch;
            stopEvent.execute(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
