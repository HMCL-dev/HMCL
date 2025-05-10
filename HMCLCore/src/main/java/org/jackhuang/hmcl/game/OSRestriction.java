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
package org.jackhuang.hmcl.game;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author huangyuhui
 */
@JsonAdapter(OSRestriction.JsonAdapterImpl.class)
public final class OSRestriction {

    private final OperatingSystem name;
    private final String version;
    private final String arch;

    public OSRestriction() {
        this(OperatingSystem.UNKNOWN);
    }

    public OSRestriction(OperatingSystem name) {
        this(name, null);
    }

    public OSRestriction(OperatingSystem name, String version) {
        this(name, version, null);
    }

    public OSRestriction(OperatingSystem name, String version, String arch) {
        this.name = name;
        this.version = version;
        this.arch = arch;
    }

    public OperatingSystem getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getArch() {
        return arch;
    }

    public boolean allow() {
        if (name != OperatingSystem.UNKNOWN && name != OperatingSystem.CURRENT_OS
                && !(name == OperatingSystem.LINUX && OperatingSystem.CURRENT_OS.isLinuxOrBSD()))
            return false;

        if (version != null)
            if (Lang.test(() -> !Pattern.compile(version).matcher(OperatingSystem.SYSTEM_VERSION).matches()))
                return false;

        if (arch != null)
            return !Lang.test(() -> !Pattern.compile(arch).matcher(Architecture.SYSTEM_ARCH.getCheckedName()).matches());

        return true;
    }

    public static final class JsonAdapterImpl implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != OSRestriction.class) {
                return null;
            }

            TypeAdapter<OSRestriction> thisDelegate = (TypeAdapter<OSRestriction>) gson.getDelegateAdapter(this, type);

            return (TypeAdapter<T>) new TypeAdapter<OSRestriction>() {
                @Override
                public void write(JsonWriter writer, OSRestriction restriction) throws IOException {
                    thisDelegate.write(writer, restriction);
                }

                @Override
                public OSRestriction read(JsonReader reader) {
                    JsonObject element = gson.fromJson(reader, JsonObject.class);

                    OSRestriction restriction = thisDelegate.fromJsonTree(element);
                    if (restriction.getName() != null) {
                        return restriction;
                    }

                    JsonElement name = element.getAsJsonObject().get("name");
                    if (name != null && name.isJsonPrimitive()) {
                        JsonPrimitive jp = name.getAsJsonPrimitive();
                        if (jp.isString()) {
                            String[] parts = jp.getAsString().split("-", 2);
                            if (parts.length == 2) {
                                OperatingSystem os = gson.fromJson(new JsonPrimitive(parts[0]), OperatingSystem.class);
                                Architecture arch = gson.fromJson(new JsonPrimitive(parts[1]), Architecture.class);
                                if (os != null && arch != null) {
                                    return new OSRestriction(os, restriction.version, arch.getCheckedName());
                                }
                            }
                        }
                    }

                    return restriction;
                }
            };
        }
    }
}
