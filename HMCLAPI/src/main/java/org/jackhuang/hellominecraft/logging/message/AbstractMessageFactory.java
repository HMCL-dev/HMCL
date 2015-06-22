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
public abstract class AbstractMessageFactory
	implements IMessageFactory {

    @Override
    public IMessage newMessage(Object message) {
	return new ObjectMessage(message);
    }

    @Override
    public IMessage newMessage(String message) {
	return new SimpleMessage(message);
    }
}
