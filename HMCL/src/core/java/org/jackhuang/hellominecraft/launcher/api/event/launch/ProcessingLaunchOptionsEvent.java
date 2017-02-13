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
package org.jackhuang.hellominecraft.launcher.api.event.launch;

import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;

/**
 * This event gets fired before generating launch command.
 * <br>
 * Pay attension: If you need to terminate the launching process, you must throw
 * {@link org.jackhuang.hellominecraft.launcher.core.RuntimeGameException anyway.
 * <br>
 * This event is fired on the {@link org.jackhuang.hellominecraft.api.HMCAPI#EVENT_BUS}
 * @param source {@link org.jackhuang.hellominecraft.launcher.core.launch.GameLauncher}
 * @param LaunchOptions you can modify the value of this event to control the launching process.
 * @author huang
 */
public class ProcessingLaunchOptionsEvent extends SimpleEvent<LaunchOptions> {
    
    public ProcessingLaunchOptionsEvent(Object source, LaunchOptions value) {
        super(source, value);
    }
    
}
