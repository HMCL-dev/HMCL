/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util;

import org.jackhuang.hellominecraft.util.lang.SupportedLocales;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 *
 * @author huangyuhui
 */
public final class C {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //http://repo1.maven.org/maven2
    public static final String URL_PUBLISH = "http://www.mcbbs.net/thread-142335-1-1.html";
    public static final String URL_TIEBA = "http://tieba.baidu.com/f?kw=hellominecraftlauncher";
    public static final String URL_GITHUB = "https://github.com/huanghongxun/HMCL/issues";
    public static final String URL_MINECRAFTFORUM = "http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-tools/1265720-hello-minecraft-launcher-1-9-3-mc-1-7-4-auto";

    public static final String URL_FORGE_LIST = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/json";
    public static final String URL_LITELOADER_LIST = "http://dl.liteloader.com/versions/versions.json";

    private C() {
    }

    public static String i18n(String a, Object... format) {
        return SupportedLocales.NOW_LOCALE.translate(a, format);
    }

}
