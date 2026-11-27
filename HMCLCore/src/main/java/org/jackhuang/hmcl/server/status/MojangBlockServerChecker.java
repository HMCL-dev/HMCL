// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.HostAndPort;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MojangBlockServerChecker {
    @VisibleForTesting
    static final String SHA_1 = "SHA-1";
    private static volatile MojangBlockServerChecker.Checker checker;

    private MojangBlockServerChecker() {

    }

    private static boolean isBlocking(@NotNull InetSocketAddress inetSocketAddress) {
        if (checker == null) {
            initChecker();
        }
        return checker.isBlocking(inetSocketAddress);
    }

    private static boolean isBlocking(@NotNull HostAndPort hostAndPort) {
        if (checker == null) {
            initChecker();
        }
        return checker.isBlocking(hostAndPort);
    }

    public static void check(@NotNull InetSocketAddress inetSocketAddress) throws ServerBlockedByMojangException {
        if (isBlocking(inetSocketAddress)) {
            throw new ServerBlockedByMojangException(inetSocketAddress.getAddress().getHostAddress());
        }
    }

    public static void check(@NotNull HostAndPort hostAndPort) throws ServerBlockedByMojangException {
        if (isBlocking(hostAndPort)) {
            throw new ServerBlockedByMojangException(hostAndPort.toString());
        }
    }

    private static void initChecker() {
        synchronized (MojangBlockServerChecker.class) {
            if (checker != null) return;

            try {
                Set<String> collected = Arrays.stream(NetworkUtils.doGet(WebURL.parse("https://sessionserver.mojang.com/blockedservers"))
                                .split("\\r?\\n"))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                checker = new Checker.CollectChecker(collected);
            } catch (Exception e) {
                LOG.error("Failed to initialize block server checker.", e);
                checker = Checker.AlwaysPassChecker.instance;
            }
        }
    }

    @TestOnly
    public static void setTestCheckContent(Set<String> collected) {
        checker = new Checker.CollectChecker(collected);
    }

    @VisibleForTesting
    static boolean isIPv4(String[] address) {
        if (address.length != 4) {
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

    private sealed interface Checker {
        boolean isBlocking(@NotNull HostAndPort hostAndPort);

        boolean isBlocking(@NotNull InetSocketAddress address);

        final class AlwaysPassChecker implements Checker {
            private static final AlwaysPassChecker instance = new AlwaysPassChecker();

            private AlwaysPassChecker() {

            }

            @Override
            public boolean isBlocking(@NotNull HostAndPort hostAndPort) {
                return false;
            }

            @Override
            public boolean isBlocking(@NotNull InetSocketAddress address) {
                return false;
            }
        }

        final class CollectChecker implements Checker {
            private final Set<String> collected;

            public CollectChecker(Set<String> collected) {
                this.collected = collected;
            }

            @Override
            public boolean isBlocking(@NotNull HostAndPort hostAndPort) {
                try {
                    return isBlocking(IDN.toASCII(hostAndPort.host()));
                } catch (IllegalArgumentException var2) {
                    return false;
                }
            }

            @Override
            public boolean isBlocking(@NotNull InetSocketAddress address) {
                InetAddress inetAddress = address.getAddress();
                if (inetAddress == null) return false;

                if (isBlocking(inetAddress.getHostAddress())) {
                    return true;
                }
                return isBlocking(inetAddress.getHostName());
            }

            private boolean isBlockedServerHostName(String server) {
                byte[] serverBytes = server.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.ISO_8859_1);

                return collected.contains(DigestUtils.digestToString(SHA_1, serverBytes));
            }

            private boolean isBlocking(@NotNull String server) {
                if (server.isEmpty()) {
                    return false;
                }
                if (server.startsWith("_minecraft._tcp.")) {
                    server = server.substring("_minecraft._tcp.".length());
                }

                while (server.endsWith(".")) {
                    server = server.substring(0, server.length() - 1);
                }
                if (server.isEmpty()) {
                    return false;
                }

                if (isBlockedServerHostName(server)) {
                    return true;
                } else {

                    String[] parts = server.split("\\.");
                    boolean isIPV4 = MojangBlockServerChecker.isIPv4(parts);
                    if (!isIPV4 && isBlockedServerHostName("*." + server)) {
                        return true;
                    }
                    while (parts.length > 1) {
                        parts = remove(parts, isIPV4 ? parts.length - 1 : 0);
                        String starredPart = isIPV4 ? String.join(".", parts) + ".*" : "*." + String.join(".", parts);
                        if (isBlockedServerHostName(starredPart)) {
                            return true;
                        }
                    }

                    return false;
                }
            }

            private @NotNull String[] remove(@NotNull String[] original, int index) {
                String[] result = new String[original.length - 1];
                System.arraycopy(original, 0, result, 0, index);
                System.arraycopy(original, index + 1, result, index, original.length - index - 1);
                return result;
            }

        }
    }

    public static class ServerBlockedByMojangException extends IOException {
        public ServerBlockedByMojangException(String message) {
            super(message);
        }
    }
}
