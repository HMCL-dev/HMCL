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
package org.jackhuang.hmcl.server.resolver;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MojangBlockServerChecker {
    private static volatile Predicate<InetSocketAddress> predicate;

    private MojangBlockServerChecker() {

    }

    public static boolean isBlocking(InetSocketAddress inetSocketAddress0) {
        if (predicate == null) {
            synchronized (MojangBlockServerChecker.class) {
                if (predicate == null) {
                    try {
                        URLConnection urlConnection = (new URL("https://sessionserver.mojang.com/blockedservers")).openConnection();
                        urlConnection.setConnectTimeout(7_000);
                        urlConnection.setReadTimeout(7_000);

                        try (InputStream is = urlConnection.getInputStream();
                             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1))
                        ) {
                            Set<String> collected = reader.lines().collect(Collectors.toUnmodifiableSet());

                            MessageDigest sha1MessageDigest = MessageDigest.getInstance("SHA-1");

                            predicate = new Predicate<>() {
                                private boolean isBlocking(@NotNull String server) {
                                    if (server.startsWith("_minecraft._tcp.")) {
                                        server = server.substring("_minecraft._tcp.".length());
                                    }

                                    while (server.charAt(server.length() - 1) == '.') {
                                        server = server.substring(0, server.length() - 1);
                                    }


                                    if (isBlockedServerHostName(collected, server)) {
                                        return true;
                                    } else {
                                        List<String> parts = new ArrayList<>(Arrays.asList(server.split("\\.")));
                                        boolean isIPV4 = isIPv4(parts);
                                        if (!isIPV4 && isBlockedServerHostName(collected, "*." + server)) {
                                            return true;
                                        }
                                        while (parts.size() > 1) {
                                            parts.remove(isIPV4 ? parts.size() - 1 : 0);
                                            String starredPart = isIPV4 ? String.join(".", parts) + ".*" : "*." + String.join(".", parts);
                                            if (isBlockedServerHostName(collected, starredPart)) {
                                                return true;
                                            }
                                        }

                                        return false;
                                    }
                                }

                                private boolean isIPv4(List<String> address) {
                                    if (address.size() != 4) {
                                        return false;
                                    }

                                    for (String s : address) {
                                        try {
                                            int part = Integer.parseInt(s);
                                            if (part < 0 || part > 255) {
                                                return false;
                                            }
                                        } catch (NumberFormatException ignore) {
                                            return false;
                                        }
                                    }
                                    return true;
                                }

                                private boolean isBlockedServerHostName(@NotNull Set<String> collect, String server) {
                                    byte[] serverBytes = server.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.ISO_8859_1);
                                    byte[] hash;
                                    synchronized (sha1MessageDigest) {
                                        hash = sha1MessageDigest.digest(serverBytes);
                                    }
                                    StringBuilder hexString = new StringBuilder();
                                    for (byte b : hash) {
                                        String hex = Integer.toHexString(0xff & b);
                                        if (hex.length() == 1) {
                                            hexString.append('0');
                                        }
                                        hexString.append(hex);
                                    }
                                    return collect.contains(hexString.toString());
                                }

                                @Override
                                public boolean test(InetSocketAddress inetSocketAddress) {
                                    if (isBlocking(inetSocketAddress.getAddress().getHostName())) {
                                        return true;
                                    }
                                    return isBlocking(inetSocketAddress.getAddress().getHostAddress());
                                }
                            };
                        }

                    } catch (Exception e) {
                        LOG.error("Failed to initialize block server checker", e);
                        predicate = (hostAndIp) -> false;
                    }
                }
            }
        }
        return predicate.test(inetSocketAddress0);
    }
}
