// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.resolver;

import org.jackhuang.hmcl.util.HostAndPort;
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
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MojangBlockServerChecker {
    private static volatile Checker checker;

    private MojangBlockServerChecker() {

    }

    public static boolean isBlocking(HostAndPort hostAndPort) {
        if (checker == null) {
            synchronized (MojangBlockServerChecker.class) {
                if (checker == null) {
                    checker = createChecker();
                }
                return checker.isBlocking(hostAndPort);
            }
        } else {
            return checker.isBlocking(hostAndPort);
        }
    }

    public static boolean isBlocking(InetSocketAddress inetSocketAddress) {
        if (checker == null) {
            synchronized (MojangBlockServerChecker.class) {
                if (checker == null) {
                    checker = createChecker();
                }
                return checker.isBlocking(inetSocketAddress);
            }
        } else {
            return checker.isBlocking(inetSocketAddress);
        }
    }

    private static @NotNull Checker createChecker() {
        try {
            URLConnection urlConnection = (new URL("https://sessionserver.mojang.com/blockedservers")).openConnection();
            urlConnection.setConnectTimeout(7_000);
            urlConnection.setReadTimeout(7_000);

            try (InputStream is = urlConnection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1))
            ) {
                Set<String> collected = reader.lines().collect(Collectors.toUnmodifiableSet());
                MessageDigest sha1MessageDigest = MessageDigest.getInstance("SHA-1");

                return new Checker() {

                    private boolean isBlockedServerHostName(String server) {
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
                        return collected.contains(hexString.toString());
                    }

                    private boolean isBlocking(@NotNull String server) {
                        if (server.startsWith("_minecraft._tcp.")) {
                            server = server.substring("_minecraft._tcp.".length());
                        }

                        while (server.charAt(server.length() - 1) == '.') {
                            server = server.substring(0, server.length() - 1);
                        }


                        if (isBlockedServerHostName(server)) {
                            return true;
                        } else {
                            List<String> parts = new ArrayList<>(Arrays.asList(server.split("\\.")));
                            boolean isIPV4 = isIPv4(parts);
                            if (!isIPV4 && isBlockedServerHostName("*." + server)) {
                                return true;
                            }
                            while (parts.size() > 1) {
                                parts.remove(isIPV4 ? parts.size() - 1 : 0);
                                String starredPart = isIPV4 ? String.join(".", parts) + ".*" : "*." + String.join(".", parts);
                                if (isBlockedServerHostName(starredPart)) {
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

                    @Override
                    public boolean isBlocking(HostAndPort hostAndPort) {

                        return isBlocking(hostAndPort.host());
                    }

                    @Override
                    public boolean isBlocking(InetSocketAddress address) {
                        if (isBlocking(address.getAddress().getHostAddress())) {
                            return true;
                        }
                        return isBlocking(address.getAddress().getHostName());
                    }
                };
            }

        } catch (Exception e) {
            LOG.error("Failed to initialize block server checker", e);
            return new Checker() {
                @Override
                public boolean isBlocking(HostAndPort hostAndPort) {
                    return false;
                }

                @Override
                public boolean isBlocking(InetSocketAddress address) {
                    return false;
                }
            };
        }
    }

    private interface Checker {
        boolean isBlocking(HostAndPort hostAndPort);

        boolean isBlocking(InetSocketAddress address);
    }
}
