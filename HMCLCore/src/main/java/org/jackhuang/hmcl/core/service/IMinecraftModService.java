/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hmcl.core.service;

import java.io.File;
import java.util.List;
import org.jackhuang.hmcl.core.mod.ModInfo;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftModService extends IMinecraftBasicService {

    public IMinecraftModService(IMinecraftService service) {
        super(service);
    }

    public abstract List<ModInfo> getMods(String id);

    public abstract List<ModInfo> recacheMods(String id);

    public abstract boolean addMod(String id, File f);

    public abstract boolean removeMod(String id, Object[] mods);
}
