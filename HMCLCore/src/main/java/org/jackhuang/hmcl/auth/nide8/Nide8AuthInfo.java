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
package org.jackhuang.hmcl.auth.nide8;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.Immutable;

import java.util.UUID;

/**
 * @author huangyuhui
 */
@Immutable
public class Nide8AuthInfo extends AuthInfo {

    private final String serverID;
    private final Nide8InjectorArtifactInfo info;

    public Nide8AuthInfo(Nide8InjectorArtifactInfo info, String serverID, String username, UUID uuid, String accessToken, String userProperties) {
        super(username, uuid, accessToken, userProperties);
        this.serverID = serverID;
        this.info = info;
    }

    public String getServerID() {
        return serverID;
    }

    /**
     * Called when launching game.
     *
     * @return null if no argument is specified
     */
    @Override
    public Arguments getLaunchArguments(LaunchOptions options) {

        return new Arguments().addJVMArguments(
                "-javaagent:" + info.getLocation().toString() + "=" + serverID,
                "-Dauthlibinjector.side=client");
    }
}
