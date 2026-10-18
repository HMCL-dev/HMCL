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
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;

/// Display orders shared by instance management and the launcher's instance switchers.
@NotNullByDefault
public enum InstanceSortType {
    /// Preserves the repository's existing version and release-time order.
    DEFAULT,
    /// Sorts instance names alphabetically.
    NAME_ASC,
    /// Sorts instance names in reverse alphabetical order.
    NAME_DESC,
    /// Sorts Minecraft versions from oldest to newest.
    VERSION_ASC,
    /// Sorts Minecraft versions from newest to oldest.
    VERSION_DESC,
    /// Groups instances by their user-defined category, then sorts their names alphabetically.
    CATEGORY;

    /// Returns a deterministic comparator; the default keeps the repository's input order.
    public Comparator<HMCLGameInstance> comparator() {
        Comparator<HMCLGameInstance> name = Comparator
                .comparing((HMCLGameInstance instance) -> instance.getId().id(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(instance -> instance.getId().id());
        Comparator<HMCLGameInstance> version = Comparator
                .comparing(HMCLGameInstance::getVersion)
                .thenComparing(instance -> {
                    @Nullable Instant releaseTime = instance.getResolvedManifest().releaseTime();
                    return releaseTime != null ? releaseTime : Instant.EPOCH;
                });
        return switch (this) {
            case DEFAULT -> (left, right) -> 0;
            case NAME_ASC -> name;
            case NAME_DESC -> name.reversed();
            case VERSION_ASC -> version.thenComparing(name);
            case VERSION_DESC -> version.reversed().thenComparing(name);
            case CATEGORY -> Comparator.comparing(HMCLGameInstance::getCategory, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(name);
        };
    }
}
