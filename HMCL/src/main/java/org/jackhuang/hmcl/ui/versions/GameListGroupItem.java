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
package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Describes a group header displayed among instance cards.
@NotNullByDefault
public final class GameListGroupItem implements GameListEntry {
    /// The stable persisted group ID, or `null` for the synthetic ungrouped section.
    private final @Nullable String id;

    /// The localized group title.
    private final String name;

    /// Number of visible instances in this group.
    private final int size;

    /// Whether the group is currently expanded.
    private boolean expanded;

    /// Animated expansion progress used by the group arrow.
    private final DoubleProperty expansionProgress = new SimpleDoubleProperty(this, "expansionProgress");

    /// Toggles expansion of this group.
    private final Runnable toggleAction;

    /// Renames this group; unavailable for the synthetic ungrouped section.
    private final @Nullable Runnable renameAction;

    /// Deletes this group; unavailable for the synthetic ungrouped section.
    private final @Nullable Runnable deleteAction;

    /// Creates a group header entry.
    public GameListGroupItem(@Nullable String id, String name, int size, boolean expanded,
                             Runnable toggleAction, @Nullable Runnable renameAction,
                             @Nullable Runnable deleteAction) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.expanded = expanded;
        this.expansionProgress.set(expanded ? 1 : 0);
        this.toggleAction = toggleAction;
        this.renameAction = renameAction;
        this.deleteAction = deleteAction;
    }

    /// Returns the stable group ID, or `null` for the ungrouped section.
    public @Nullable String getId() {
        return id;
    }

    /// Returns the group title.
    public String getName() {
        return name;
    }

    /// Returns the visible instance count.
    public int getSize() {
        return size;
    }

    /// Returns whether the group is expanded.
    public boolean isExpanded() {
        return expanded;
    }

    /// Sets whether the group is logically expanded.
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    /// Returns the animated expansion progress, from `0` to `1`.
    public DoubleProperty expansionProgressProperty() {
        return expansionProgress;
    }

    /// Sets the animated expansion progress.
    public void setExpansionProgress(double expansionProgress) {
        this.expansionProgress.set(expansionProgress);
    }

    /// Toggles group expansion.
    public void toggle() {
        toggleAction.run();
    }

    /// Returns whether this persisted group can be managed.
    public boolean isManageable() {
        return renameAction != null && deleteAction != null;
    }

    /// Opens the rename dialog when supported.
    public void rename() {
        if (renameAction != null) {
            renameAction.run();
        }
    }

    /// Opens the delete confirmation when supported.
    public void delete() {
        if (deleteAction != null) {
            deleteAction.run();
        }
    }
}
