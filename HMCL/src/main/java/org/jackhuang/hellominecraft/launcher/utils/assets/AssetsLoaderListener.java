/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.util.ArrayList;

/**
 *
 * @author hyh
 */
public interface AssetsLoaderListener {

    void OnDone(ArrayList<Contents> loader);

    void OnFailed(Exception e);
}