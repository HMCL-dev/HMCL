// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status.pinger;

import org.jackhuang.hmcl.server.status.MojangBlockServerChecker;
import org.jackhuang.hmcl.server.status.ServerAddressDnsSrvResolver;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.util.HostAndPort;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed interface ServerStatusPinger permits Legacy1_6ServerStatusPinger, LegacyOlderServerStatusPinger, ModernServerStatusPinger {
    List<? extends @NotNull ServerStatusPinger> PINGERS = List.of(
            ModernServerStatusPinger.INSTANCE,
            Legacy1_6ServerStatusPinger.INSTANCE,
            LegacyOlderServerStatusPinger.INSTANCE
    );

    static @NotNull ServerStatusResult getStatus(@NotNull String serverIp) {
        try {
            ServerAddress originalServerAddress = ServerAddress.parse(serverIp);

            ResolvedServerAddress resolvedServerAddress;
            try {
                resolvedServerAddress = resolve(originalServerAddress);
            } catch (MojangBlockServerChecker.ServerBlockedByMojangException e) {
                return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.BLOCKED_BY_MOJANG, e);
            } catch (UnknownHostException e) {
                return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.UNKNOWN_HOST, e);
            }

            List<Exception> failures = new ArrayList<>();
            for (ServerStatusPinger pinger : PINGERS) {
                Thread.sleep(pinger.delayToPing());
                ServerStatusResult statusResult;
                try {
                    statusResult = pinger.getStatus(resolvedServerAddress);
                } catch (Exception e) {
                    statusResult = ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
                }

                if (statusResult.isSucceed()) {
                    return statusResult;
                } else {
                    Exception exception = ((ServerStatusResult.FailureResult) statusResult).getException();
                    failures.add(exception);
                    if (shouldBreakPing(exception)) {
                        break;
                    }
                }
            }

            Exception collectException = new IOException("Failed to get the status of server: " + serverIp);
            for (Exception failure : failures) {
                collectException.addSuppressed(failure);
            }
            throw collectException;
        } catch (Exception e) {
            LOG.error("Failed to get the status of server: " + serverIp, e);
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
        }
    }

    static boolean shouldBreakPing(@NotNull Exception e) {
        if (e instanceof StatusParseException spe) {
            return spe.shouldBreak;
        }
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        return e instanceof ConnectException;
    }

    private static @NotNull ResolvedServerAddress resolve(@NotNull ServerAddress serverAddress) throws MojangBlockServerChecker.ServerBlockedByMojangException, UnknownHostException {
        InetSocketAddress resolvedAddress = null;
        UnknownHostException originalResolveException = null;

        try {
            resolvedAddress = serverAddress.getHostAndPort().createInetSocketAddress();
            MojangBlockServerChecker.check(resolvedAddress);
        } catch (UnknownHostException e) {
            originalResolveException = e;
        }

        MojangBlockServerChecker.check(serverAddress.getHostAndPort());

        HostAndPort dnsSrvTarget = ServerAddressDnsSrvResolver.resolve(serverAddress);
        if (dnsSrvTarget != null) {
            resolvedAddress = dnsSrvTarget.createInetSocketAddress();
            MojangBlockServerChecker.check(resolvedAddress);
        }

        if (resolvedAddress == null) {
            throw originalResolveException;
        }

        return new ResolvedServerAddress(serverAddress, resolvedAddress);
    }

    @NotNull ServerStatusResult getStatus(@NotNull ResolvedServerAddress resolvedServerAddress) throws Exception;

    default long delayToPing() {
        return 5_000;
    }

    @FunctionalInterface
    interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    interface DataReader<T> {
        T read(DataInputStream in) throws IOException;
    }

    final class ResolvedServerAddress {
        private final @NotNull ServerAddress originalServerAddress;
        private final @NotNull InetSocketAddress resolvedAddress;

        private final @NotNull String hostForIntentPacket;
        private final int portForIntentPacket;

        public ResolvedServerAddress(
                @NotNull ServerAddress originalServerAddress,
                @NotNull InetSocketAddress resolvedAddress
        ) {
            this.originalServerAddress = originalServerAddress;
            this.resolvedAddress = resolvedAddress;

            boolean isOriginalAndResolvedSame = originalServerAddress.getHostAndPort().equals(resolvedAddress.getHostName(), resolvedAddress.getPort());
            boolean existingResolvedAddress = originalServerAddress.getQueryProperties().get("_o") != null;

            if (isOriginalAndResolvedSame && !existingResolvedAddress) {
                portForIntentPacket = originalServerAddress.getHostAndPort().port();
                hostForIntentPacket = NetworkUtils.withQuery(originalServerAddress.getHostAndPort().host(), originalServerAddress.getQueryProperties());
            } else {
                Map<String, String> newExtraProperties = new LinkedHashMap<>(originalServerAddress.getQueryProperties());

                newExtraProperties.remove("_o");
                newExtraProperties.put("_o", originalServerAddress.getHostAndPort().host() + ":" + originalServerAddress.getHostAndPort().port());

                portForIntentPacket = resolvedAddress.getPort();
                hostForIntentPacket = NetworkUtils.withQuery(resolvedAddress.getHostName(), newExtraProperties);
            }
        }

        PingConnection createConnection() throws IOException {
            return new PingConnection(resolvedAddress);
        }

        public @NotNull ServerAddress getOriginalServerAddress() {
            return originalServerAddress;
        }

        public @NotNull InetSocketAddress getResolvedAddress() {
            return resolvedAddress;
        }

        public int getPortForIntentPacket() {
            return portForIntentPacket;
        }

        public @NotNull String getHostForIntentPacket() {
            return hostForIntentPacket;
        }
    }

    final class PingConnection implements Closeable {
        private final @NotNull Socket socket = new Socket();
        private final @NotNull DataOutputStream out;
        private final @NotNull DataInputStream in;

        private PingConnection(@NotNull InetSocketAddress address) throws IOException {
            try {
                this.socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
                this.socket.connect(address, 7_000);
                this.socket.setSoTimeout(7_000);

                this.out = new DataOutputStream(socket.getOutputStream());
                this.in = new DataInputStream(socket.getInputStream());
            } catch (Throwable e) {
                try {
                    socket.close();
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                }
                throw e;
            }
        }

        public @NotNull DataInputStream getIn() {
            return in;
        }

        public @NotNull DataOutputStream getOut() {
            return out;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    class StatusParseException extends IOException {
        private final boolean shouldBreak;

        public StatusParseException(String message, Throwable cause, boolean shouldBreak) {
            super(message, cause);
            this.shouldBreak = shouldBreak;
        }
    }
}
