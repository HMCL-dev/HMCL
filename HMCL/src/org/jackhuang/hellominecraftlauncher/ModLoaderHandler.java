/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.io.File;
import java.net.URLClassLoader;

/**
 *
 * @author hyh
 */
public class ModLoaderHandler {

    private boolean ended = false;

    public void check(String line) {
        if (this.ended) {
            return;
        }
        if (line == null) {
            return;
        }
        if ((!line.equals("Done.")
                && (!line.startsWith("Starting up SoundSystem"))
                && (!line.startsWith("Loading: net.java.games.input."))
                && (!line.startsWith("Found animation info for:"))
                ? 0 : 1) != 0) {
            this.ended = true;
            Launcher.restoreMods();
        }
    }

    ModLoaderHandler() {
        super();
        ended = false;
    }
}