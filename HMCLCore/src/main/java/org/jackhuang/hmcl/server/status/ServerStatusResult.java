// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract sealed class ServerStatusResult {
    public static SuccessResult success(@NotNull ServerStatus status) {
        return new SuccessResult(status);
    }

    public static FailureResult failure(@NotNull FailureResult.Reason reason, @NotNull Exception exception) {
        return new FailureResult(reason, exception);
    }

    public boolean isSucceed() {
        return this instanceof SuccessResult;
    }

    public boolean isFailure() {
        return this instanceof FailureResult;
    }

    public @Nullable ServerStatus getIfSucceed() {
        if (this instanceof SuccessResult successResult) {
            return successResult.status;
        }
        return null;
    }

    public @Nullable FailureResult.Reason getFailureReasonIfFailed() {
        if (this instanceof FailureResult failureResult) {
            return failureResult.reason;
        }
        return null;
    }

    public static final class SuccessResult extends ServerStatusResult {
        private final @NotNull ServerStatus status;

        private SuccessResult(@NotNull ServerStatus status) {
            this.status = status;
        }

        public @NotNull ServerStatus getServerStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "SuccessResult{" +
                    "status=" + status +
                    '}';
        }
    }

    public static final class FailureResult extends ServerStatusResult {
        private final @NotNull Reason reason;
        private final @NotNull Exception exception;

        private FailureResult(@NotNull Reason reason, @NotNull Exception exception) {
            this.reason = reason;
            this.exception = exception;
        }

        public @NotNull Reason getReason() {
            return reason;
        }

        public @NotNull Exception getException() {
            return exception;
        }

        @Override
        public String toString() {
            return "FailureResult{" +
                    "reason=" + reason +
                    ", exception=" + exception +
                    '}';
        }

        public enum Reason {
            EXCEPTION,
            UNKNOWN_HOST,
            BLOCKED_BY_MOJANG
        }
    }
}
