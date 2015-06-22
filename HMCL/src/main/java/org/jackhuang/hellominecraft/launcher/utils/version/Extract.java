/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

import java.util.Arrays;

/**
 *
 * @author huangyuhui
 */
public class Extract extends Object implements Cloneable {
    String[] exclude;

    @Override
    protected Object clone() {
        Extract e = new Extract();
        e.exclude = exclude == null ? null : Arrays.copyOf(exclude, exclude.length);
        return e;
    }
    
    
}
