/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 *
 * @author hyh
 */
public class MinecraftHandler extends Handler {
    
    private boolean restore;

    MinecraftHandler() {
        restore = false;
    }

    @Override
    public final void publish(LogRecord logrecord) {
        if (restore) {
            return;
        }
        String s = logrecord.getMessage();
        if (s == null) {
            return;
        }
        if (logrecord.getLevel().intValue() < Level.INFO.intValue()) {
            return;
        }
        if (s.startsWith("Forge Mod Loader has successfully loaded") && s.endsWith("mods")) {
            restore = true;
        }
        if (restore) {
            Launcher.restoreMods();
        }
    }

    @Override
    public final void close() {
    }

    @Override
    public final void flush() {
    }
}
