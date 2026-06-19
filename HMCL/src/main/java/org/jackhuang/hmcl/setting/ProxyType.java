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
