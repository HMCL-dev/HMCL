/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft;

import org.jackhuang.hellominecraft.logging.logger.Logger;


/**
 *
 * @author hyh
 */
public class HMCLog {
    
    public static Logger logger = new Logger("HMC");
    
    public static void log(String message) {
        logger.info(message);
    }
    
    public static void warn(String message) {
        logger.warn(message);
    }
    
    public static void warn(String msg, Throwable t) {
        logger.warn(msg, t);
    }
    
    public static void err(String msg) {
        logger.error(msg);
    }
    
    public static void err(String msg, Throwable t) {
        logger.error(msg, t);
    }
    
}
