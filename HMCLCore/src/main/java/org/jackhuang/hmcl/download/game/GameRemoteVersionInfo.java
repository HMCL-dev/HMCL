/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.Date;

/**
 *
 * @author huangyuhui
 */
public final class GameRemoteVersionInfo implements Validation {

    @SerializedName("id")
    private final String gameVersion;

    @SerializedName("time")
    private final Date time;

    @SerializedName("releaseTime")
    private final Date releaseTime;

    @SerializedName("type")
    private final ReleaseType type;

    @SerializedName("url")
    private final String url;

    public GameRemoteVersionInfo() {
        this("", new Date(), new Date(), ReleaseType.UNKNOWN);
    }

    public GameRemoteVersionInfo(String gameVersion, Date time, Date releaseTime, ReleaseType type) {
        this(gameVersion, time, releaseTime, type, Constants.DEFAULT_LIBRARY_URL + gameVersion + "/" + gameVersion + ".json");
    }

    public GameRemoteVersionInfo(String gameVersion, Date time, Date releaseTime, ReleaseType type, String url) {
        this.gameVersion = gameVersion;
        this.time = time;
        this.releaseTime = releaseTime;
        this.type = type;
        this.url = url;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Date getTime() {
        return time;
    }

    public Date getReleaseTime() {
        return releaseTime;
    }

    public ReleaseType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }
    
    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(gameVersion))
            throw new JsonParseException("GameRemoteVersion id cannot be blank");
        if (StringUtils.isBlank(url))
            throw new JsonParseException("GameRemoteVersion url cannot be blank");
    }
}
