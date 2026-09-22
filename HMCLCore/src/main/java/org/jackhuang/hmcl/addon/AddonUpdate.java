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
package org.jackhuang.hmcl.addon;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public record AddonUpdate(
        LocalAddonFile localAddonFile,
        RemoteAddon.Version currentVersion,
        RemoteAddon.Version targetVersion
) {

    @SuppressWarnings("RedundantRecordConstructor")
    public record UpdateConditions(RemoteAddon.Type type, @Nullable List<Predicate<RemoteAddon.Version>> predicates) {
        public UpdateConditions {
        }
    }

}
