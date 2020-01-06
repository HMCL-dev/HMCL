/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class ForgeInstall {

    private final String profileName;
    private final String target;
    private final Artifact path;
    private final String version;
    private final String filePath;
    private final String welcome;
    private final String minecraft;
    private final String mirrorList;
    private final String logo;

    public ForgeInstall() {
        this(null, null, null, null, null, null, null, null, null);
    }

    public ForgeInstall(String profileName, String target, Artifact path, String version, String filePath, String welcome, String minecraft, String mirrorList, String logo) {
        this.profileName = profileName;
        this.target = target;
        this.path = path;
        this.version = version;
        this.filePath = filePath;
        this.welcome = welcome;
        this.minecraft = minecraft;
        this.mirrorList = mirrorList;
        this.logo = logo;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getTarget() {
        return target;
    }

    public Artifact getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getWelcome() {
        return welcome;
    }

    public String getMinecraft() {
        return minecraft;
    }

    public String getMirrorList() {
        return mirrorList;
    }

    public String getLogo() {
        return logo;
    }

}
