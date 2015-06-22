/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging.layout;

import org.jackhuang.hellominecraft.logging.LogEvent;

/**
 *
 * @author hyh
 */
public abstract class AbstractStringLayout implements ILayout<String> {

    @Override
    public byte[] toByteArray(LogEvent event) {
	return toSerializable(event).getBytes();
    }
    
}
