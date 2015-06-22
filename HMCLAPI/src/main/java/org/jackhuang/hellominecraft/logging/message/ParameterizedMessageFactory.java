/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.message;

/**
 *
 * @author hyh
 */
public final class ParameterizedMessageFactory extends AbstractMessageFactory {

    public static final ParameterizedMessageFactory INSTANCE = new ParameterizedMessageFactory();

    @Override
    public IMessage newMessage(String message, Object[] params) {
	return new ParameterizedMessage(message, params);
    }
}
