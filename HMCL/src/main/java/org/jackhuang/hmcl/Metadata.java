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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.JarUtils;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {}

    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.thisJar().flatMap(JarUtils::getImplementationVersion).orElse("@develop@"));
    public static final String NAME = "HMCL";
    public static final String TITLE = NAME + " " + VERSION;
    
    public static final String UPDATE_URL = System.getProperty("hmcl.update_source.override", "https://hmcl.huangyuhui.net/api/update_link");
    public static final String CONTACT_URL = "https://www.huangyuhui.net/hmcl.php";
    public static final String PUBLISH_URL = "http://www.mcbbs.net/thread-142335-1-1.html";
}
