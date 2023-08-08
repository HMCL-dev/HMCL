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
package org.jackhuang.hmcl.util;

import java.nio.file.Path;
import java.util.Set;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * Utility for Adding JavaFX to module path.
 *
 * @author ZekerZhayard
 */
public final class JavaFXPatcher {
    private JavaFXPatcher() {
    }

    public static void patch(Set<String> modules, Path[] jarPaths, String[] addOpens) {
        LOG.info("No need to patch JavaFX with Java 8");
    }
}
