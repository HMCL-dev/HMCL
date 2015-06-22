/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging.layout;

import java.io.Serializable;
import org.jackhuang.hellominecraft.logging.LogEvent;

/**
 *
 * @author hyh
 * @param <T>
 */
public interface ILayout<T extends Serializable> {
    
    byte[] toByteArray(LogEvent event);
    T toSerializable(LogEvent event);
    
}
