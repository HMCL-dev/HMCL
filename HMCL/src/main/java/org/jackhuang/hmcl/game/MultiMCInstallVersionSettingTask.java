/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.mod.multimc.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;

import java.util.Objects;

public final class MultiMCInstallVersionSettingTask extends Task<Void> {
    private final Profile profile;
    private final MultiMCInstanceConfiguration manifest;
    private final String version;

    public MultiMCInstallVersionSettingTask(Profile profile, MultiMCInstanceConfiguration manifest, String version) {
        this.profile = profile;
        this.manifest = manifest;
        this.version = version;

        setExecutor(Schedulers.javafx());
    }

    @Override
    public void execute() {
        VersionSetting vs = Objects.requireNonNull(profile.getRepository().specializeVersionSetting(version));
        ModpackHelper.toVersionSetting(manifest, vs);
    }
}
