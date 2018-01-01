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
package org.jackhuang.hmcl.download.optifine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.NetworkUtils;

/**
 *
 * @author huangyuhui
 */
public final class OptiFineDownloadFormatter extends TaskResult<String> {

    private final String url;

    public OptiFineDownloadFormatter(String url) {
        this.url = url;
    }

    @Override
    public void execute() throws Exception {
        String result = null;
        String content = NetworkUtils.doGet(NetworkUtils.toURL(url));
        Matcher m = PATTERN.matcher(content);
        while (m.find())
            result = m.group(1);
        if (result == null)
            throw new IllegalStateException("Cannot find version in " + content);
        setResult("http://optifine.net/downloadx?f=OptiFine" + result);
    }

    @Override
    public String getId() {
        return ID;
    }

    public static final String ID = "optifine_formatter";
    private static final Pattern PATTERN = Pattern.compile("\"downloadx\\?f=OptiFine(.*)\"");
}
