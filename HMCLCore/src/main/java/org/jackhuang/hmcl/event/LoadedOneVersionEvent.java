/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.game.Version;

import java.util.EventObject;

/**
 * This event gets fired when a minecraft version has been loaded.
 * <br>
 * This event is fired on the {@link org.jackhuang.hmcl.event.EventBus#EVENT_BUS}
 *
 * @author huangyuhui
 */
public final class LoadedOneVersionEvent extends Event {

    private final Version version;

    /**
     *
     * @param source {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the version id.
     */
    public LoadedOneVersionEvent(Object source, Version version) {
        super(source);
        this.version = version;
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public boolean hasResult() {
        return true;
    }
}
