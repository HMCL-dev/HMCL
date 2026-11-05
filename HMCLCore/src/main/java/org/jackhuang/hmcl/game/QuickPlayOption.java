// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

/// The quick play option.
///
/// @see <a href="https://minecraft.wiki/w/Quick_Play">Quick Play - Minecraft Wiki</a>
public sealed interface QuickPlayOption {
    record SinglePlayer(String worldFolderName) implements QuickPlayOption {
    }

    record MultiPlayer(String serverIP) implements QuickPlayOption {
    }

    record Realm(String realmID) implements QuickPlayOption {
    }
}
