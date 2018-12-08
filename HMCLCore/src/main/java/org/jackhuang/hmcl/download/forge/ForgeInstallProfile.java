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
package org.jackhuang.hmcl.download.forge;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class ForgeInstallProfile implements Validation {

    @SerializedName("install")
    private final ForgeInstall install;

    @SerializedName("versionInfo")
    private final Version versionInfo;

    public ForgeInstallProfile(ForgeInstall install, Version versionInfo) {
        this.install = install;
        this.versionInfo = versionInfo;
    }

    public ForgeInstall getInstall() {
        return install;
    }

    public Version getVersionInfo() {
        return versionInfo;
    }

    @Override
    public void validate() throws JsonParseException {
        if (install == null)
            throw new JsonParseException("InstallProfile install cannot be null");

        if (versionInfo == null)
            throw new JsonParseException("InstallProfile versionInfo cannot be null");
    }
}
