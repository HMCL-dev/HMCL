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
package org.jackhuang.hellominecraft.launcher.core.version;

import java.io.File;
import java.util.ArrayList;

/**
 *
 * @author huangyuhui
 */
public class ModpackUpdater {

    ModpackInfo info;

    public ModpackUpdater(File baseFolder, ModpackInfo info) {
        this.info = info;
    }

    void update() {

    }

    public static class ModpackInfo {

        ArrayList<ModpackFolder> folders;
        ArrayList<ModpackFile> files;

        public static class ModpackFolder {

            String ext, name;
        }

        public static class ModpackFile {

            String hash, loc;
        }
    }
}
