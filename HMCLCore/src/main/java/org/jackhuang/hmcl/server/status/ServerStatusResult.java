// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jackhuang.hmcl.server.pinger.ServerStatusPinger;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract sealed class ServerStatusResult {
    public static SuccessResult success(@NotNull ServerAddress rawServerAddress, @NotNull ServerStatusPinger pinger, long networkLatency, @NotNull ServerStatus status) {
        return new SuccessResult(rawServerAddress, pinger, networkLatency, status);
    }

    public static FailureResult failure(FailureResult.Reason reason, Exception exception) {
        return new FailureResult(reason, exception);
    }

    public @Nullable ServerStatus getIfSucceed() {
        if (this instanceof SuccessResult success) {
            return success.status;
        }
        return null;
    }

    public @Nullable ServerStatusResult.FailureResult.Reason getFailureReasonIfFailed() {
        if (this instanceof FailureResult failure) {
            return failure.reason;
        }
        return null;
    }

    public boolean isSuccess() {
        return this instanceof SuccessResult;
    }

    public boolean isFailure() {
        return this instanceof FailureResult;
    }

    public static final class SuccessResult extends ServerStatusResult {
        private final ServerAddress rawServerAddress;
        private final ServerStatusPinger pinger;
        private final long networkLatency;
        private final ServerStatus status;

        private SuccessResult(ServerAddress rawServerAddress, ServerStatusPinger pinger, long networkLatency, ServerStatus status) {
            this.rawServerAddress = rawServerAddress;
            this.pinger = pinger;
            this.networkLatency = networkLatency;
            this.status = status;
        }

        public ServerAddress getRawServerAddress() {
            return rawServerAddress;
        }

        public ServerStatusPinger getPinger() {
            return pinger;
        }

        public ServerStatus getServerStatus() {
            return status;
        }

        public long getNetworkLatency() {
            return networkLatency;
        }
    }

    public static final class FailureResult extends ServerStatusResult {
        private final Reason reason;
        private final Exception exception;

        private FailureResult(Reason reason, Exception exception) {
            this.reason = reason;
            this.exception = exception;
        }

        public Reason getReason() {
            return reason;
        }

        public Exception getException() {
            return exception;
        }

        public enum Reason {
            EXCEPTION,
            UNKNOWN_HOST,
            BLOCKED_BY_MOJANG
        }
    }
}
