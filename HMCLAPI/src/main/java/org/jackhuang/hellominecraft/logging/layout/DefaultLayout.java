/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.logging.layout;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.jackhuang.hellominecraft.logging.LogEvent;

/**
 *
 * @author hyh
 */
public class DefaultLayout extends AbstractStringLayout {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    @Override
    public String toSerializable(LogEvent event) {
	return "[" + sdf.format(new Date()) + "][" + event.threadName + "/" + event.level.name() + "] " + event.message.getFormattedMessage() + "\n";
    }
    
}
