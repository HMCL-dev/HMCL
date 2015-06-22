/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author hyh
 */
public class ReflectUtils {
    
    public static Set<? extends Class<?>> getClasses(Class c) {
	HashSet set = new HashSet();
	set.addAll(Arrays.asList(c.getInterfaces()));
	while(c != Object.class) {
	    set.add(c);
	    c = c.getSuperclass();
	}
	return set;
    }
    
}
