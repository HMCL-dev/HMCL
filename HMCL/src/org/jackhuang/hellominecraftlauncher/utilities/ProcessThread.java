/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.jackhuang.hellominecraftlauncher.JavaProcess;

/**
 *
 * @author hyh
 */
public class ProcessThread extends Thread {

    JavaProcess p;
    ProcessListener l;

    public interface ProcessListener {

        void onPrintln(String line);

        void onStop();
    }

    public ProcessThread(JavaProcess process, ProcessListener listener) {
        p = process;
        l = listener;
    }

    public JavaProcess getProcess() {
        return p;
    }

    @Override
    public void run() {
        try {
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(p.getRawProcess().getInputStream()));
            String line;
            while (p.isRunning()) {
                if((line = bufferedReader.readLine()) == null)
                    break;
                if (l != null) l.onPrintln(line);
                System.out.println(line);
                p.getSysOutLines().add(line);
            }
            if (l != null) {
                l.onStop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }
}
