/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.setting.Profile;

import java.net.Proxy;

/**
 * @author huangyuhui
 */
public class HMCLDependencyManager extends DefaultDependencyManager {
    private final Profile profile;

    public HMCLDependencyManager(Profile profile, DownloadProvider downloadProvider) {
        this(profile, downloadProvider, Proxy.NO_PROXY);
    }

    public HMCLDependencyManager(Profile profile, DownloadProvider downloadProvider, Proxy proxy) {
        super(profile.getRepository(), downloadProvider, proxy);

        this.profile = profile;
    }

    @Override
    public GameBuilder gameBuilder() {
        return new HMCLGameBuilder(profile);
    }
}
