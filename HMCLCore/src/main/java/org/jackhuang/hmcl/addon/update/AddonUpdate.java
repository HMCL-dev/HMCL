// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public record AddonUpdate(
        LocalAddonFile<?> localAddonFile,
        RemoteAddon.Version currentVersion,
        RemoteAddon.Version targetVersion
) {
}
