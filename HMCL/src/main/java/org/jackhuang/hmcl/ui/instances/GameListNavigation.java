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
package org.jackhuang.hmcl.ui.instances;

import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Finds launch-list choices while retaining hidden instances as anchors in the display order.
@NotNullByDefault
public final class GameListNavigation {

    /// Prevents instantiation of this navigation utility.
    private GameListNavigation() {
    }

    /// Finds the next visible instance, wrapping around and skipping launch-list exclusions.
    /// A current instance absent from the list starts navigation from its first or last visible item.
    ///
    /// @param instances the complete display order, including launch-list exclusions
    /// @param currentId the current instance ID, or `null` when there is no current selection
    /// @param direction a negative value for the previous item, a positive value for the next,
    ///                  or zero to leave the selection unchanged
    /// @return the visible destination, or `null` for zero direction or no visible choices
    public static @Nullable HMCLGameInstance findAdjacent(
            List<HMCLGameInstance> instances, @Nullable GameInstanceID currentId, int direction) {
        if (direction == 0 || instances.isEmpty()) {
            return null;
        }

        int step = direction > 0 ? 1 : -1;
        int index = Lang.indexWhere(instances, instance -> instance.getId().equals(currentId));
        if (index < 0) {
            index = step > 0 ? -1 : 0;
        }

        // Check at most one full cycle so an entirely hidden list cannot loop indefinitely.
        for (int visited = 0; visited < instances.size(); visited++) {
            index = Math.floorMod(index + step, instances.size());
            HMCLGameInstance candidate = instances.get(index);
            if (candidate.isShownInLaunchMenu()) {
                return candidate;
            }
        }
        return null;
    }
}
