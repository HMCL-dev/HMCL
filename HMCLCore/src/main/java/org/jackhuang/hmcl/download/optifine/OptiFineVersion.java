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
package org.jackhuang.hmcl.download.optifine;

import com.google.gson.annotations.SerializedName;

/**
 *
 * @author huangyuhui
 */
public final class OptiFineVersion {

    @SerializedName("dl")
    private final String downloadLink;

    @SerializedName("ver")
    private final String version;

    @SerializedName("date")
    private final String date;

    @SerializedName("type")
    private final String type;

    @SerializedName("patch")
    private final String patch;

    @SerializedName("mirror")
    private final String mirror;

    @SerializedName("mcversion")
    private final String gameVersion;

    public OptiFineVersion() {
        this(null, null, null, null, null, null, null);
    }

    public OptiFineVersion(String downloadLink, String version, String date, String type, String patch, String mirror, String gameVersion) {
        this.downloadLink = downloadLink;
        this.version = version;
        this.date = date;
        this.type = type;
        this.patch = patch;
        this.mirror = mirror;
        this.gameVersion = gameVersion;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    public String getVersion() {
        return version;
    }

    public String getDate() {
        return date;
    }

    public String getType() {
        return type;
    }

    public String getPatch() {
        return patch;
    }

    public String getMirror() {
        return mirror;
    }

    public String getGameVersion() {
        return gameVersion;
    }
}
