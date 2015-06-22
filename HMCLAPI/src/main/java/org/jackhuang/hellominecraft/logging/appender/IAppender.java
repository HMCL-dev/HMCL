/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging.appender;

import java.io.Serializable;
import org.jackhuang.hellominecraft.logging.LogEvent;
import org.jackhuang.hellominecraft.logging.layout.ILayout;

/**
 *
 * @author hyh
 */
public interface IAppender {
    
    void append(LogEvent event);
    String getName();
    boolean ignoreExceptions();
    ILayout<? extends Serializable> getLayout();
    
}
