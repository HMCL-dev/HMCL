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

/// HMCL repository status snapshot, parallel to [HMCLGameInstance] in the instance hierarchy.
@NotNullByDefault
public class HMCLGameRepositoryStatus extends DefaultGameRepositoryStatus {
    /// Creates an empty unsealed HMCL status.
    ///
    /// @param repository the owning repository
    /// @param layout     the HMCL layout for this snapshot
    public HMCLGameRepositoryStatus(HMCLGameRepository repository, HMCLGameRepositoryLayout layout) {
        super(repository, layout);
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    @Override
    protected HMCLGameRepositoryStatus newEmpty() {
        return new HMCLGameRepositoryStatus(getRepository(), getLayout());
    }

    @Override
    public HMCLGameRepositoryStatus clone() {
        return (HMCLGameRepositoryStatus) super.clone();
    }
}
