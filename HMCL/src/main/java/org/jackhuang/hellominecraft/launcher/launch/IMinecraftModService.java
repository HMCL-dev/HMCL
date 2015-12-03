/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.util.List;
import org.jackhuang.hellominecraft.launcher.utils.ModInfo;

/**
 *
 * @author huangyuhui
 */
public interface IMinecraftModService {

    List<ModInfo> getMods();

    List<ModInfo> recacheMods();

    boolean addMod(File f);

    void removeMod(int[] index);
}
