/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// A wrapper for `Map<String, Object>`, supporting type-safe reading and writing of values.
///
///  @author Glavo
public final class SettingsMap {
    public record Key<T>(String key) {
    }

    private final Map<String, Object> map = new HashMap<>();

    public SettingsMap() {
    }

    public Map<String, Object> asStringMap() {
        return map;
    }

    public boolean containsKey(@NotNull Key<?> key) {
        return map.containsKey(key.key);
    }

    public boolean containsKey(@NotNull String key) {
        return map.containsKey(key);
    }

    public <T> @Nullable T get(@NotNull Key<T> key) {
        @SuppressWarnings("unchecked")
        T value = (T) map.get(key.key);
        return value;
    }

    public Object get(@NotNull String key) {
        return map.get(key);
    }

    public <T> T getOrDefault(@NotNull Key<T> key, T defaultValue) {
        @SuppressWarnings("unchecked")
        T value = (T) map.get(key.key);
        return value != null ? value : defaultValue;
    }

    public <T> T put(@NotNull Key<T> key, @Nullable T value) {
        @SuppressWarnings("unchecked")
        T result = (T) map.put(key.key, value);
        return result;
    }

    public Object put(@NotNull String key, @Nullable Object value) {
        return map.put(key, value);
    }

    public <T> T remove(@NotNull Key<T> key) {
        @SuppressWarnings("unchecked")
        T result = (T) map.remove(key.key);
        return result;
    }

    public Object remove(@NotNull String key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }
}
