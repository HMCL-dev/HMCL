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
package org.jackhuang.hellominecraft.launcher.api.event.version;

import java.io.File;
import java.util.EventObject;
import org.jackhuang.hellominecraft.util.Wrapper;

/**
 * This event gets fired when we getting minecraft library path.
 * <br>
 * This event is fired on the {@link org.jackhuang.hellominecraft.api.HMCLAPI#EVENT_BUS}
 * @param source {@link org.jackhuang.hellominecraft.launcher.core.version.MinecraftLibrary}
 * @param {@code Wrapper<File>} modify this thing to change to your wanted mc lib.
 * @author huangyuhui
 */
public class MinecraftLibraryPathEvent extends EventObject {
    
    String location;
    Wrapper<File> file;
    
    public MinecraftLibraryPathEvent(Object source, String location, Wrapper<File> value) {
        super(source);
        this.location = location;
        this.file = value;
    }

    public String getLocation() {
        return location;
    }

    public Wrapper<File> getFile() {
        return file;
    }
    
    
}
