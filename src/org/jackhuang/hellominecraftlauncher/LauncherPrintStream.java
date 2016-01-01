/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;

/**
 *
 * @author hyh
 */
public final class LauncherPrintStream extends PrintStream {

    private ModLoaderHandler handler = new ModLoaderHandler();

    public LauncherPrintStream(OutputStream outputStream) {
        super(outputStream);
    }

    public final void println(String line) {
        super.println(line);
        this.handler.check(line);
    }
}