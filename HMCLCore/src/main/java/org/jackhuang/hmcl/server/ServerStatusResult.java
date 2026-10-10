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

import org.jetbrains.annotations.Nullable;

public sealed class ServerStatusResult {
    public static ServerStatusResult success(ServerStatus serverStatus) {
        return new SuccessResult(serverStatus);
    }

    public static ServerStatusResult failure(FailureResult.Reason reason, Exception exception) {
        return new FailureResult(reason, exception);
    }

    public @Nullable ServerStatus getIfSucceed() {
        if (this instanceof SuccessResult success) {
            return success.serverStatus;
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
        public final ServerStatus serverStatus;

        private SuccessResult(ServerStatus serverStatus) {
            this.serverStatus = serverStatus;
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
