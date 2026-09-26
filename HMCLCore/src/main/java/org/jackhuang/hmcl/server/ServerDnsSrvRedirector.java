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
package org.jackhuang.hmcl.server;

import org.jetbrains.annotations.NotNull;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.Hashtable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ServerDnsSrvRedirector {
    private static volatile DnsSrvRedirector redirector;

    private ServerDnsSrvRedirector() {

    }

    static @NotNull ServerAddress lookup(ServerAddress original) throws IOException {
        if (redirector == null) {
            synchronized (ServerDnsSrvRedirector.class) {
                if (redirector == null) {

                    try {
                        Class.forName("com.sun.jndi.dns.DnsContextFactory");
                        Hashtable<String, String> env = new Hashtable<>();
                        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                        env.put("java.naming.provider.url", "dns:");
                        env.put("com.sun.jndi.dns.timeout.retries", "1");
                        DirContext context = new InitialDirContext(env);

                        redirector = (address) -> {
                            if (address.port() != 25565) return address;
                            try {
                                Attributes attributes = context.getAttributes("_minecraft._tcp." + address.host(), new String[]{"SRV"});
                                Attribute srvAttribute = attributes.get("srv");
                                if (srvAttribute != null) {
                                    String[] arguments = srvAttribute.get().toString().split(" ", 4);
                                    return new ServerAddress(arguments[3], ServerAddress.parsePort(arguments[2]));
                                }
                            } catch (Throwable ignored) {
                            }
                            return address;
                        };
                    } catch (Throwable e) {
                        LOG.error("Failed to initialize SRV redirect resolved, some servers might not work", e);
                        redirector = (address) -> address;
                    }
                }
            }
        }
        return redirector.lookup(original);
    }

    @FunctionalInterface
    private interface DnsSrvRedirector {
        @NotNull ServerAddress lookup(@NotNull ServerAddress original) throws IOException;
    }
}
