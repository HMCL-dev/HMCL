/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging;

/**
 *
 * @author hyh
 */
public class LoggingException extends RuntimeException {

    public LoggingException(Exception e) {
	super(e);
    }
    
}
