/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

/**
 *
 * @author hyh
 */
public class Natives implements Cloneable {
    public String windows, osx, linux;

    @Override
    protected Object clone() {
        Natives n = new Natives();
        n.windows = windows;
        n.osx = osx;
        n.linux = linux;
        return n;
    }
}
