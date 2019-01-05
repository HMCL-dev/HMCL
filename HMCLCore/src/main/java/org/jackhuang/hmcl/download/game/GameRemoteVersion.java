/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.util.Immutable;

import java.util.Date;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class GameRemoteVersion extends RemoteVersion {

    private final ReleaseType type;
    private final Date time;

    public GameRemoteVersion(String gameVersion, String selfVersion, String url, ReleaseType type, Date time) {
        super(gameVersion, selfVersion, url, getReleaseType(type));
        this.type = type;
        this.time = time;
    }

    public Date getTime() {
        return time;
    }

    public ReleaseType getType() {
        return type;
    }

    @Override
    public int compareTo(RemoteVersion o) {
        if (!(o instanceof GameRemoteVersion))
            return 0;

        return ((GameRemoteVersion) o).getTime().compareTo(getTime());
    }

    private static Type getReleaseType(ReleaseType type) {
        if (type == null) return Type.UNCATEGORIZED;
        switch (type) {
            case RELEASE:
                return Type.RELEASE;
            case SNAPSHOT:
                return Type.SNAPSHOT;
            case UNKNOWN:
                return Type.UNCATEGORIZED;
            default:
                return Type.OLD;
        }
    }
}
