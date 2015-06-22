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
public abstract interface IMessageFactory {

    public abstract IMessage newMessage(Object paramObject);

    public abstract IMessage newMessage(String paramString);

    public abstract IMessage newMessage(String paramString, Object[] paramArrayOfObject);
}
