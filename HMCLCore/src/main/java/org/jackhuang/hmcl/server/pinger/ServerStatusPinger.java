// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed interface ServerStatusPinger permits LegacyServerStatusPinger, ModernServerStatusPinger {
    List<ServerStatusPinger> PINGERS = List.of(
            ModernServerStatusPinger.instance,
            LegacyServerStatusPinger.instance
    );

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
                    failures.add((ServerStatusResult.FailureResult) statusResult);
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
}
