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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

public sealed abstract class Either<L, R> {

    public static <L, R> Either<L, R> left(@NotNull L left) {
        return new Left<>(left);
    }

    public static <L, R> Either<L, R> right(@NotNull R right) {
        return new Right<>(right);
    }

    public abstract boolean hasLeft();

    public abstract boolean hasRight();

    public abstract L left();

    public abstract R right();

    public abstract <T> T map(Function<L, T> lFunc, Function<R, T> rFunc);

    private static final class Left<L, R> extends Either<L, R> {

        private final L value;

        private Left(L value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean hasLeft() {
            return true;
        }

        @Override
        public boolean hasRight() {
            return false;
        }

        @Override
        public L left() {
            return value;
        }

        @Override
        public R right() {
            return null;
        }

        @Override
        public <T> T map(Function<L, T> lFunc, Function<R, T> rFunc) {
            return lFunc.apply(value);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Either.Left<?,?> el) {
                return this.value.equals(el.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class Right<L, R> extends Either<L, R> {

        private final R value;

        private Right(R value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean hasLeft() {
            return false;
        }

        @Override
        public boolean hasRight() {
            return true;
        }

        @Override
        public L left() {
            return null;
        }

        @Override
        public R right() {
            return value;
        }

        @Override
        public <T> T map(Function<L, T> lFunc, Function<R, T> rFunc) {
            return rFunc.apply(value);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Either.Right<?,?> el) {
                return this.value.equals(el.value);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value.hashCode() + 31;
        }
    }

}
