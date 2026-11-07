// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

/// The quick play option.
///
/// @see <a href="https://minecraft.wiki/w/Quick_Play">Quick Play - Minecraft Wiki</a>
public sealed interface QuickPlayOption {
    static boolean isSupportedVersion(GameVersionNumber gameVersionNumber) {
        return gameVersionNumber.isAtLeast("1.20", "23w14a");
    }

    record SinglePlayer(String worldFolderName) implements QuickPlayOption {
    }

    record MultiPlayer(String serverIP) implements QuickPlayOption {
    }

    record Realm(String realmID) implements QuickPlayOption {
    }
}
