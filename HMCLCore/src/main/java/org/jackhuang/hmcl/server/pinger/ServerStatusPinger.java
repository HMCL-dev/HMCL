// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed interface ServerStatusPinger permits OldServerStatusPinger, ClassicServerStatusPinger, LegacyServerStatusPinger, ModernServerStatusPinger {

    //  Both Alpha, Beta And Classic require simulated login to obtain the relevant data.
    //  If their protocol numbers do not match, the connection will be terminated immediately.
    //  Therefore, they must be tested one by one;
    List<ServerStatusPinger> PINGERS = List.of(
            ModernServerStatusPinger.instance,
            LegacyServerStatusPinger.instance
//            OldServerStatusPinger.BetaServerStatusPinger.instance,
//            OldServerStatusPinger.AlphaServerStatusPinger.instance,
//            ClassicServerStatusPinger.instance
    );

    static String generateFakeName() {
        return UUID.randomUUID().toString().replace("-", "").substring(
                0, ThreadLocalRandom.current().nextInt(3, 17)
        );
    }

    static @NotNull ServerStatusResult getStatus(@NotNull String serverIp) throws IOException {
        try {
            ServerAddress rawAddress = ServerAddress.parse(serverIp);
            ServerAddressResolveResult resolveResult = rawAddress.resolve();

            if (resolveResult instanceof ServerAddressResolveResult.FailureResult failureResult) {
                return ServerStatusResult.failure(failureResult.getReason().getStatusReason(), failureResult.getException());
            }

            ServerAddressResolveResult.SuccessResult successResult = (ServerAddressResolveResult.SuccessResult) resolveResult;
            List<ServerStatusResult.FailureResult> failures = new ArrayList<>();
            for (ServerStatusPinger pinger : PINGERS) {
                ServerStatusResult statusResult = pinger.getStatus(successResult);
                if (statusResult.isSuccess()) {
                    return statusResult;
                } else {
                    ServerStatusResult.FailureResult failureResult = (ServerStatusResult.FailureResult) statusResult;
                    failures.add(failureResult);
                    if (shouldBreakPing(failureResult.getException())) {
                        break;
                    }
                }
            }

            Exception collectException = new IOException("Failed to get server status");
            for (ServerStatusResult.FailureResult failureResult : failures) {
                collectException.addSuppressed(failureResult.getException());
            }
            LOG.error("Failed to get the status of server " + serverIp, collectException);
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, collectException);
        } catch (Exception e) {
            LOG.error("Failed to get the status of server " + serverIp, e);
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
        }
    }

    @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult);

    @NotNull String getName();

    @FunctionalInterface
    interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    interface DataReader<T> {
        T read(DataInputStream in) throws IOException;
    }

    static Connection createConnection(@NotNull ServerAddressResolveResult.SuccessResult successResult) throws IOException {
        return new Connection(successResult.getConnectAddress());
    }

    static boolean shouldBreakPing(@NotNull Exception e) {
        return e instanceof ConnectException;
    }

    final class Connection implements Closeable {
        private final Socket socket;
        private final DataOutputStream out;
        private final DataInputStream in;

        private Connection(@NotNull InetSocketAddress address) throws IOException {
            this.socket = new Socket();

            this.socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
            this.socket.connect(address, 7_000);
            this.socket.setSoTimeout(7_000);

            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        }

        public DataInputStream getIn() {
            return in;
        }

        public DataOutputStream getOut() {
            return out;
        }

        @Override
        public void close() throws IOException {
            in.close();
            out.close();
            socket.close();
        }
    }
}
