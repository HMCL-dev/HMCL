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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.launch.DefaultLauncher;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.CommandBuilder;

import java.util.List;
import java.util.Map;

/**
 * @author huangyuhui
 */
public final class HMCLGameLauncher extends DefaultLauncher {

    public HMCLGameLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options) {
        this(repository, versionId, authInfo, options, null);
    }

    public HMCLGameLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, versionId, authInfo, options, listener, true);
    }

    public HMCLGameLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        super(repository, versionId, authInfo, options, listener, daemon);
    }

    @Override
    protected Map<String, String> getConfigurations() {
        Map<String, String> res = super.getConfigurations();
        res.put("${launcher_name}", Launcher.NAME);
        res.put("${launcher_version}", Launcher.VERSION);
        return res;
    }
}
