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

import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;

/**
 * This event gets fired when loading versions in a .minecraft folder.
 * <br>
 * This event is fired on the {@link org.jackhuang.hellominecraft.api.HMCAPI#EVENT_BUS}
 * @param source {@link org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersionManager}
 * @param IMinecraftService .minecraft folder.
 * @author huang
 */
public class RefreshingVersionsEvent extends SimpleEvent<IMinecraftService> {
    
    public RefreshingVersionsEvent(Object source, IMinecraftService value) {
        super(source, value);
    }
    
}
