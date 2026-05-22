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

import org.jackhuang.hmcl.setting.GameSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

/// Stores a direct setting value that can be inherited by removing its name from `overrideProperties`.
///
/// @author Glavo
@NotNullByDefault
public final class SimpleInheritableProperty<T extends @UnknownNullability Object>
        extends SimpleSettingProperty<T>
        implements InheritableProperty<T> {

    /// Creates a property with the given owner, serialized name, and direct default value.
    public SimpleInheritableProperty(GameSetting bean, String name, T defaultValue) {
        super(bean, name, defaultValue);
    }
}
