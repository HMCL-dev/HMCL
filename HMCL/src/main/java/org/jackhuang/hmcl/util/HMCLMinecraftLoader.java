/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util;

import java.util.List;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.api.game.LaunchOptions;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.launch.MinecraftLoader;
import org.jackhuang.hmcl.core.service.IMinecraftService;

/**
 *
 * @author huang
 */
public class HMCLMinecraftLoader extends MinecraftLoader {
    
    public HMCLMinecraftLoader(LaunchOptions p, IMinecraftService provider, UserProfileProvider lr) throws GameException {
        super(p, provider, lr);
    }

    @Override
    protected void appendJVMArgs(List<String> list) {
        super.appendJVMArgs(list);
        
        list.add("-Dminecraft.launcher.version=" + Main.LAUNCHER_VERSION);
        list.add("-Dminecraft.launcher.brand=" + Main.LAUNCHER_NAME);
        
        boolean flag = false;
        for (String s : list) if (s.contains("-Dlog4j.configurationFile=")) flag = true;
        if (!flag) {
            list.add("-Dlog4j.configurationFile=" + Main.LOG4J_FILE.getAbsolutePath());
        }
    }
}
