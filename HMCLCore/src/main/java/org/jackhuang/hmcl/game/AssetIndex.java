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

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class AssetIndex {

    @SerializedName("virtual")
    private final boolean virtual;

    @SerializedName("objects")
    private final Map<String, AssetObject> objects;

    public AssetIndex() {
        this(false, Collections.emptyMap());
    }

    public AssetIndex(boolean virtual, Map<String, AssetObject> objects) {
        this.virtual = virtual;
        this.objects = new HashMap<>(objects);
    }

    public boolean isVirtual() {
        return virtual;
    }

    public Map<String, AssetObject> getObjects() {
        return Collections.unmodifiableMap(objects);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("virtual", virtual).append("objects", objects).toString();
    }
}
