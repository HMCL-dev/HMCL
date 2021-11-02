/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.Objects;

public abstract class Result<T> {

    public T get() {
        throw new IllegalStateException("TriState not ok");
    }

    public boolean isOK() {
        return false;
    }

    public boolean isError() {
        return false;
    }

    public static <T> Result<T> ok(T result) {
        return new OK<>(Objects.requireNonNull(result));
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> error() {
        return (Result<T>) Error.INSTANCE;
    }

    private static class OK<T> extends Result<T> {
        private final T result;

        public OK(T result) {
            this.result = result;
        }

        @Override
        public T get() {
            return result;
        }
    }

    private static class Error<T> extends Result<T> {
        public static final Error<Void> INSTANCE = new Error<>();

        @Override
        public boolean isError() {
            return true;
        }
    }

}
