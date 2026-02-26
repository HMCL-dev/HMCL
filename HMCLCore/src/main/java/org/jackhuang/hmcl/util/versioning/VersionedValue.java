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
package org.jackhuang.hmcl.util.versioning;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Represents a value that varies based on the game version.
 * <p>
 * This class uses a {@link TreeMap} to store values associated with minimum version thresholds.
 * When retrieving a value for a specific version, it returns the value associated with the
 * highest version key that is less than or equal to the requested version.
 * </p>
 *
 * @param <T> The type of the value being versioned.
 */
public class VersionedValue<T> {
    private final TreeMap<GameVersionNumber, T> versionValues = new TreeMap<>();

    public VersionedValue() {
    }

    public VersionedValue(String minVersion, T value) {
        versionValues.put(GameVersionNumber.asGameVersion(minVersion), value);
    }

    public void putMinVersion(String version, T value) {
        versionValues.put(GameVersionNumber.asGameVersion(version), value);
    }

    public Optional<T> getValue(String version) {
        return getValue(GameVersionNumber.asGameVersion(version));
    }

    public Optional<T> getValue(GameVersionNumber version) {
        return Optional.ofNullable(versionValues.floorEntry(version)).map(Map.Entry::getValue);
    }

    public T getValue(String version, T defaultValue) {
        return getValue(version).orElse(defaultValue);
    }

    public T getValue(GameVersionNumber version, T defaultValue) {
        return getValue(version).orElse(defaultValue);
    }

    public TreeMap<GameVersionNumber, T> asMap() {
        return versionValues;
    }

    public void putAll(VersionedValue<T> other) {
        versionValues.putAll(other.asMap());
    }
}
