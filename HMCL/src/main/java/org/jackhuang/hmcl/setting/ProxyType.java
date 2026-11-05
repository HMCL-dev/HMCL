// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.Proxy;

/// Launcher proxy selection mode.
///
/// @author Glavo
@NotNullByDefault
public enum ProxyType {
    /// Use the proxy selector provided by the host JVM or operating system.
    SYSTEM(null),

    /// Disable proxy usage explicitly.
    DIRECT(Proxy.Type.DIRECT),

    /// Use a manually configured HTTP proxy.
    HTTP(Proxy.Type.HTTP),

    /// Use a manually configured SOCKS proxy.
    SOCKS(Proxy.Type.SOCKS);

    /// The matching JDK proxy type, or `null` when this mode does not map to a concrete proxy.
    private final @Nullable Proxy.Type jdkType;

    /// Creates a proxy selection mode.
    ///
    /// @param jdkType the matching JDK proxy type, or `null` when this mode does not map to a concrete proxy
    ProxyType(@Nullable Proxy.Type jdkType) {
        this.jdkType = jdkType;
    }

    /// Returns the matching JDK proxy type.
    ///
    /// @return the JDK proxy type, or `null` when this mode does not map to a concrete proxy
    public @Nullable Proxy.Type jdkType() {
        return jdkType;
    }

    /// Returns whether this mode needs a custom proxy host and port.
    public boolean usesCustomAddress() {
        return this == HTTP || this == SOCKS;
    }
}
