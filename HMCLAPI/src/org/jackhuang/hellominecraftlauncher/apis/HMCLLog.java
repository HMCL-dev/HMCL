/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author hyh
 */
public class HMCLLog {
    
    public static Logger logger = LogManager.getLogger("HMCL");
    
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
