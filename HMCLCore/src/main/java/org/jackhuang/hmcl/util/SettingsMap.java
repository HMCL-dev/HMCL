// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// A wrapper for `Map<String, Object>`, supporting type-safe reading and writing of values.
///
/// @author Glavo
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

    @Override
    public String toString() {
        return "SettingsMap" + map;
    }
}
