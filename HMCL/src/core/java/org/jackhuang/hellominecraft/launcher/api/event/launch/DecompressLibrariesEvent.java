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

import org.jackhuang.hellominecraft.api.ResultedSimpleEvent;

/**
 * This event gets fired when we are launching a game and going to decompress natives.
 * <br>
 * This event is {@link org.jackhuang.hellominecraft.api.ResultedEvent}
 * If this event is failed, the launching process will be terminated.
 * <br>
 * This event is fired on the {@link org.jackhuang.hellominecraft.api.HMCLAPI#EVENT_BUS}
 * @param source {@link org.jackhuang.hellominecraft.launcher.core.launch.GameLauncher}
 * @param DecompressLibraryJob libraries to be decompressed
 * @author huangyuhui
 */
public class DecompressLibrariesEvent extends ResultedSimpleEvent<DecompressLibraryJob> {
    
    public DecompressLibrariesEvent(Object source, DecompressLibraryJob value) {
        super(source, value);
    }
    
}
