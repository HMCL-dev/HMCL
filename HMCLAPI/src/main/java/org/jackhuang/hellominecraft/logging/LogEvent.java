/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging;

import org.jackhuang.hellominecraft.logging.message.IMessage;

/**
 *
 * @author hyh
 */
public class LogEvent {
    
    public Level level;
    public String threadName;
    public Throwable thrown;
    public IMessage message;
    
}
