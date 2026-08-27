/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.forge;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 *
 * @author huangyuhui
 */
@Immutable
public record ForgeVersion(String branch, String mcversion, String jobver, String version, int build, long modified,
                           String[][] files) implements Validation {

    @Override
    public void validate() throws JsonParseException {
        if (files == null)
            throw new JsonParseException("ForgeVersion files cannot be null");
        if (version == null)
            throw new JsonParseException("ForgeVersion version cannot be null");
        if (mcversion == null)
            throw new JsonParseException("ForgeVersion mcversion cannot be null");
    }
}
