/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 *
 * @author huangyuhui
 */
public final class LoggingInfo implements Validation {

    @SerializedName("file")
    private final IdDownloadInfo file;
    @SerializedName("argument")
    private final String argument;
    @SerializedName("type")
    private final String type;

    public LoggingInfo() {
        this(new IdDownloadInfo());
    }

    public LoggingInfo(IdDownloadInfo file) {
        this(file, "");
    }

    public LoggingInfo(IdDownloadInfo file, String argument) {
        this(file, argument, "");
    }

    public LoggingInfo(IdDownloadInfo file, String argument, String type) {
        this.file = file;
        this.argument = argument;
        this.type = type;
    }

    public IdDownloadInfo getFile() {
        return file;
    }

    public String getArgument() {
        return argument;
    }

    public String getType() {
        return type;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        file.validate();
        if (StringUtils.isBlank(argument))
            throw new JsonParseException("LoggingInfo.argument is empty.");
        if (StringUtils.isBlank(type))
            throw new JsonParseException("LoggingInfo.type is empty.");
    }
}
