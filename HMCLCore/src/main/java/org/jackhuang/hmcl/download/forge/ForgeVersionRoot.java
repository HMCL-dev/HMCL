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

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
@Immutable
public record ForgeVersionRoot(String artifact, String webpath, String adfly, String homepage, String name,
                               Map<String, int[]> branches, Map<String, int[]> mcversion, Map<String, Integer> promos,
                               Map<Integer, ForgeVersion> number) implements Validation {
    @Override
    public void validate() throws JsonParseException {
        if (number == null)
            throw new JsonParseException("ForgeVersionRoot number cannot be null");
        if (mcversion == null)
            throw new JsonParseException("ForgeVersionRoot mcversion cannot be null");
    }
}
