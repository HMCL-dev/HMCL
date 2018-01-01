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
package org.jackhuang.hmcl.download;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jackhuang.hmcl.task.Task;

/**
 * The builder which provide a task to build Minecraft environment.
 *
 * @author huangyuhui
 */
public abstract class GameBuilder {

    protected String name = "";
    protected String gameVersion = "";
    protected Map<String, String> toolVersions = new HashMap<>();

    public String getName() {
        return name;
    }

    /**
     * The new game version name, for .minecraft/<version name>.
     *
     * @param name the name of new game version.
     */
    public GameBuilder name(String name) {
        this.name = Objects.requireNonNull(name);
        return this;
    }

    public GameBuilder gameVersion(String version) {
        this.gameVersion = Objects.requireNonNull(version);
        return this;
    }

    /**
     * @param id the core library id. i.e. "forge", "liteloader", "optifine"
     * @param version the version of the core library. For documents, you can first try [VersionList.versions]
     */
    public GameBuilder version(String id, String version) {
        if ("game".equals(id))
            gameVersion(version);
        else
            toolVersions.put(id, version);
        return this;
    }

    /**
     * @return the task that can build thw whole Minecraft environment
     */
    public abstract Task buildAsync();
}
