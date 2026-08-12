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
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes the lifecycle state of a [GameRepositoryDraft].
@NotNullByDefault
public enum GameRepositoryDraftState {
    /// The draft accepts changes and may be committed or aborted.
    OPEN,

    /// The draft is applying files and publishing its immutable successor snapshot.
    COMMITTING,

    /// The draft completed its commit and no longer accepts changes.
    COMMITTED,

    /// The draft discarded its changes and no longer accepts changes.
    ABORTED,

    /// The draft could not complete a commit or cleanup operation.
    FAILED
}
