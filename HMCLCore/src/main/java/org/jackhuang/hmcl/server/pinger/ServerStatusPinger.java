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

public interface ServerStatusPinger {
    List<ServerStatusPinger> PINGERS = List.of(
            ModernServerStatusPinger.instance,
            LegacyServerStatusPinger.instance
    );

    static @NotNull ServerStatusResult getStatus(@NotNull String serverIp) throws IOException {
        try {
            ServerAddress address = ServerAddress.parse(serverIp);
            ServerAddressResolveResult resolveResult = address.resolve();

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

    @FunctionalInterface
    interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    interface DataReader<T> {
        T read(DataInputStream in) throws IOException;
    }
}
