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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {}

    public static final String NAME = "HMCL";
    public static final String FULL_NAME = "Hello Minecraft! Launcher";
    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.thisJar().flatMap(JarUtils::getImplementationVersion).orElse("@develop@"));

    public static final String TITLE = NAME + " " + VERSION;
    public static final String FULL_TITLE = FULL_NAME + " v" + VERSION;

    public static final String UPDATE_URL = System.getProperty("hmcl.update_source.override", "https://hmcl.huangyuhui.net/api/update_link");
    public static final String CONTACT_URL = "https://hmcl.huangyuhui.net/contact";
    public static final String HELP_URL = "https://hmcl.huangyuhui.net/help";
    public static final String CHANGELOG_URL = "https://hmcl.huangyuhui.net/changelog/";
    public static final String PUBLISH_URL = "http://www.mcbbs.net/thread-142335-1-1.html";

    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path HMCL_DIRECTORY = getHMCLDirectory();

    private static Path getHMCLDirectory() {
        String home = System.getProperty("user.home", ".");
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            // to fulfill XDG standard.
            return Paths.get(home, ".cache", "hmcl");
        }
        return OperatingSystem.getWorkingDirectory("hmcl");
    }
}
