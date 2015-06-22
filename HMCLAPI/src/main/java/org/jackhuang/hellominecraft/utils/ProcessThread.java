/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public class ProcessThread extends Thread {

    JavaProcess p;
    boolean readError = false, enableReading = true;
    
    public final EventHandler<String> printlnEvent = new EventHandler<String>(this);
    public final EventHandler<JavaProcess> stopEvent = new EventHandler<JavaProcess>(this);

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
        InputStream in = null;
        BufferedReader br = null;
        if (enableReading) {
            in = readError ? p.getRawProcess().getErrorStream() : p.getRawProcess().getInputStream();
        }
        try {
            if (enableReading) {
                try {
                    br = new BufferedReader(new InputStreamReader(in, System.getProperty("sun.jnu.encoding", "UTF-8")));
                } catch (UnsupportedEncodingException ex) {
                    HMCLog.warn("Unsupported encoding: " + System.getProperty("sun.jnu.encoding", "UTF-8"), ex);
                    br = new BufferedReader(new InputStreamReader(in));
                }
            }
            
            String line;
            while (p.isRunning()) {
                if (enableReading) {
                    while ((line = br.readLine()) != null) {
                        printlnEvent.execute(line);
                        if (readError) {
                            System.err.println(line);
                            p.getStdErrLines().add(line);
                        } else {
                            System.out.println(line);
                            p.getStdOutLines().add(line);
                        }
                    }
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {
                    }
                }
            }
            if (enableReading) {
                while ((line = br.readLine()) != null) {
                    printlnEvent.execute(line);
                    if (readError) {
                        System.err.println(line);
                        p.getStdErrLines().add(line);
                    } else {
                        System.out.println(line);
                        p.getStdOutLines().add(line);
                    }
                }
            }
            stopEvent.execute(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopped() {
    }
}
