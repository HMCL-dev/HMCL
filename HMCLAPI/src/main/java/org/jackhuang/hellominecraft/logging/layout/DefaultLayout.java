/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.logging.layout;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.jackhuang.hellominecraft.logging.LogEvent;

/**
 *
 * @author huangyuhui
 */
public class DefaultLayout extends AbstractStringLayout {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    @Override
    public String toSerializable(LogEvent event) {
	return "[" + sdf.format(new Date()) + "] [" + event.threadName + "/" + event.level.name() + "] " + event.message.getFormattedMessage() + "\n";
    }
    
}
