/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.mojang;

import org.jackhuang.hmcl.download.java.JavaRemoteVersion;
import org.jackhuang.hmcl.game.GameJavaVersion;

/**
 * @author Glavo
 */
public final class MojangJavaRemoteVersion implements JavaRemoteVersion {
    private final GameJavaVersion gameJavaVersion;

    public MojangJavaRemoteVersion(GameJavaVersion gameJavaVersion) {
        this.gameJavaVersion = gameJavaVersion;
    }

    public GameJavaVersion getGameJavaVersion() {
        return gameJavaVersion;
    }

    @Override
    public int getJdkVersion() {
        return gameJavaVersion.majorVersion();
    }

    @Override
    public String getJavaVersion() {
        return String.valueOf(getJdkVersion());
    }

    @Override
    public String getDistributionVersion() {
        return String.valueOf(getJdkVersion());
    }

    @Override
    public String toString() {
        return "MojangJavaRemoteVersion[gameJavaVersion=" + gameJavaVersion + "]";
    }
}
