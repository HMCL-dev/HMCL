/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.models;

/**
 *
 * @author hyh
 */
public class ControllerAction<T> {
    /**
     * -1 - NULL
     * 1 - delete version<br />
     * 2 - new version<br />
     * 3 - copy version<br />
     * 4 - import version<br />
     * 5 - import folder<br />
     * 6 - cannot find minecraft.jar<br />
     * 7 - find the same name in the list<br />
     */
    int action;
    T content;

    public ControllerAction(int action, T content) {
        this.action = action;
        this.content = content;
    }
    
}
