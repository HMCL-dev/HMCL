/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting.property;

import com.google.gson.JsonElement;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.setting.GameSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/// @author Glavo
public class SimpleSettingProperty<T extends @UnknownNullability Object> extends SimpleObjectProperty<T>
        implements SettingProperty<T> {
    private @Nullable JsonElement rawJson;

    private final T defaultValue;

    public SimpleSettingProperty(@NotNull GameSetting bean,
                                 String name,
                                 T defaultValue) {
        super(bean, name, defaultValue);
        this.defaultValue = defaultValue;
    }

    @Override
    public @NotNull GameSetting getBean() {
        return (GameSetting) super.getBean();
    }

    @Override
    public @UnknownNullability T defaultValue() {
        return defaultValue;
    }

    @Override
    public @Nullable JsonElement getRawJson() {
        return rawJson;
    }

    @Override
    public void setRawJson(@Nullable JsonElement rawJson) {
        this.rawJson = rawJson;
    }

}
