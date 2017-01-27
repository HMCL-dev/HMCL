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

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class Extract implements Cloneable {

    @SerializedName("exclude")
    public List<String> exclude = new ArrayList<>();

    public boolean allow(String path) {
        return !StrUtils.startsWithOne(exclude, path);
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
    }

}
