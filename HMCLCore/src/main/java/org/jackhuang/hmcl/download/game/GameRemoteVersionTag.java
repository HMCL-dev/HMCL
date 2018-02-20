/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.util.Immutable;

import java.util.Date;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class GameRemoteVersionTag {

    private final ReleaseType type;
    private final Date time;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public GameRemoteVersionTag() {
        this(ReleaseType.UNKNOWN, new Date());
    }

    public GameRemoteVersionTag(ReleaseType type, Date time) {
        this.type = type;
        this.time = time;
    }

    public Date getTime() {
        return time;
    }

    public ReleaseType getType() {
        return type;
    }
}
