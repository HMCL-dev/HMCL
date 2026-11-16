// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jackhuang.hmcl.util.HostAndPort;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ServerAddressDnsSrvResolver {
    private static volatile SrvResolver srvResolver;

    private ServerAddressDnsSrvResolver() {

    }

    public static @Nullable HostAndPort resolve(@NotNull ServerAddress address) {
        if (srvResolver == null) {
            initResolver();
        }
        return srvResolver.resolve(address);
    }

    private static void initResolver() {
        synchronized (ServerAddressDnsSrvResolver.class) {
            if (srvResolver != null) return;

            try {
                Class.forName("com.sun.jndi.dns.DnsContextFactory");
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                env.put("java.naming.provider.url", "dns:");
                env.put("com.sun.jndi.dns.timeout.retries", "1");
                DirContext context = new InitialDirContext(env);
                Object lockObject = new Object();

                srvResolver = (address) -> {
                    if (address.getHostAndPort().port() != 25565) {
                        return null;
                    }

                    try {
                        Attributes attributes;
                        String name = "_minecraft._tcp." + address.getHostAndPort().host();
                        String[] attrIds = {"SRV"};
                        synchronized (lockObject) {
                            attributes = context.getAttributes(name, attrIds);
                        }
                        Attribute srvAttribute = attributes.get("srv");
                        if (srvAttribute != null) {
                            String[] arguments = srvAttribute.get().toString().split("\\s+", 4);

                            int port = 25565;
                            try {
                                port = Integer.parseInt(arguments[2]);
                            } catch (Exception ignored) {
                            }

                            return new HostAndPort(arguments[3], port);
                        }
                    } catch (Throwable ignored) {
                    }
                    return null;
                };
            } catch (Exception e) {
                LOG.error("Failed to initialize SRV redirect resolved, some servers might not work.", e);
                srvResolver = (address) -> null;
            }
        }
    }

    @FunctionalInterface
    private interface SrvResolver {
        @Nullable HostAndPort resolve(@NotNull ServerAddress address);
    }
}
