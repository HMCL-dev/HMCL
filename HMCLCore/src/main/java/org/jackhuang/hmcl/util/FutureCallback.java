// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

@FunctionalInterface
public interface FutureCallback<T> {

    /// Callback of future, called after future finishes.
    /// This callback gives the feedback whether the result of future is acceptable or not,
    /// if not, giving the reason, and future will be relaunched when necessary.
    ///
    /// @param result  result of the future
    /// @param handler handler to accept or reject the result
    void call(T result, ResultHandler handler);

    interface ResultHandler {

        /// Accept the result.
        void resolve();

        /// Reject the result with given reason.
        void reject(String reason);
    }
}
