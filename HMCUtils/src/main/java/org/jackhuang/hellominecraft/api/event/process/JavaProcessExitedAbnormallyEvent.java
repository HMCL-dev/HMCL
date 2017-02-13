/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.api.event.process;

import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.util.sys.JavaProcess;

/**
 * This event gets fired when a JavaProcess exited abnormally and the exit code is not zero.
 * <br>
 * This event is fired on the {@link org.jackhuang.hellominecraft.api.HMCAPI#EVENT_BUS}
 * @param source {@link org.jackhuang.hellominecraft.util.sys.JavaProcessMonitor}
 * @param JavaProcess The process that exited abnormally.
 * @author huangyuhui
 */
public class JavaProcessExitedAbnormallyEvent extends SimpleEvent<JavaProcess> {

    public JavaProcessExitedAbnormallyEvent(Object source, JavaProcess value) {
        super(source, value);
    }

}
