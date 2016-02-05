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
package org.jackhuang.hellominecraft.util.ui;

import java.io.OutputStream;
import java.util.Timer;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public class LogWindowOutputStream extends OutputStream {

    private static final Timer TIMER = new Timer();

    private final LogWindow txt;
    private final Level sas;

    /*
     * private final CacheTask t = new CacheTask();
     * private class CacheTask extends TimerTask {
     * private final Object lock = new Object();
     * private StringBuilder cachedString = new StringBuilder();
     *
     * @Override
     * public void run() {
     * synchronized(lock) {
     * SwingUtilities.invokeLater(() -> {
     * String t = txt.getText() + cachedString.toString().replace("\t", " ");
     * txt.setText(t);
     * txt.setCaretPosition(t.length());
     * cachedString = new StringBuilder();
     * });
     * }
     * }
     *
     * void cache(String s) {
     * synchronized(lock) {
     * cachedString.append(s);
     * }
     * }
     * }
     */
    public LogWindowOutputStream(LogWindow logWindow, Level l) {
        txt = logWindow;
        this.sas = l;
    }

    @Override
    public final void write(byte[] paramArrayOfByte) {
        write(paramArrayOfByte, 0, paramArrayOfByte.length);
    }

    @Override
    public final void write(byte[] paramArrayOfByte, int off, int len) {
        append(new String(paramArrayOfByte, off, len));
    }

    private void append(final String newString) {
        try {
            SwingUtilities.invokeLater(() -> {
                txt.log(newString, Level.guessLevel(newString, sas));
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public final void write(int paramInt) {
        append(new String(new byte[] { (byte) paramInt }));
    }

    public static void dispose() {
        TIMER.cancel();
    }
}
