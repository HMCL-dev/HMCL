/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
