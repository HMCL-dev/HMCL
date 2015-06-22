/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging.message;

import java.io.Serializable;

/**
 *
 * @author hyh
 */
public interface IMessage extends Serializable {
    String getFormattedMessage();
    String getFormat();
    Object[] getParameters();
    Throwable getThrowable();
}
