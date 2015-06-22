/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.download;

/**
 *
 * @author hyh
 */
public class NetException extends RuntimeException {
    
    public NetException(String message) {
        super(message);
    }
    
    public NetException(String message, Exception e) {
        super(message, e);
    }
    
}
