// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jetbrains.annotations.NotNull;

public sealed interface ServerVersionInfo {

    static FullServerVersionInfo of(int protocolVersion, @NotNull String protocolName) {
        return new FullServerVersionInfo(protocolVersion, protocolName);
    }

    static OnlyProtocolVersionServerVersionInfo of(int protocolVersion) {
        return new OnlyProtocolVersionServerVersionInfo(protocolVersion);
    }

    static UnknownServerVersionInfo of() {
        return UnknownServerVersionInfo.instance;
    }

    final class UnknownServerVersionInfo implements ServerVersionInfo {
        static UnknownServerVersionInfo instance = new UnknownServerVersionInfo();

        private UnknownServerVersionInfo() {
        }
    }

    record OnlyProtocolVersionServerVersionInfo(int protocolVersion) implements ServerVersionInfo {
    }

    record FullServerVersionInfo(int protocolVersion, @NotNull String protocolName) implements ServerVersionInfo {
    }
}
