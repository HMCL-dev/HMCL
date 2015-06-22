/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.utils;

/**
 *
 * @author hyh
 */
public final class Validate {
    
    public static <T> T notNull(T o) {
	if(o == null) {
	    throw new IllegalArgumentException("The validated object is null");
	}
	return o;
    }
    
}
