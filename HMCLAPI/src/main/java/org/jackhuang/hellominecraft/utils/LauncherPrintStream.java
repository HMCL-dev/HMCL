/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.utils.functions.Consumer;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 *
 * @author hyh
 */
public class LauncherPrintStream extends PrintStream {

    private final ArrayList<Consumer<String>> printListeners = new ArrayList<>();

    public LauncherPrintStream(OutputStream paramOutputStream) {
	super(paramOutputStream);
    }

    @Override
    public final void println(String paramString) {
	super.println(paramString);

	for (Consumer<String> a1 : printListeners) {
	    a1.accept(paramString);
	}
    }

    public final void addPrintListener(Consumer<String> paraml) {
	this.printListeners.add(paraml);
    }
}
