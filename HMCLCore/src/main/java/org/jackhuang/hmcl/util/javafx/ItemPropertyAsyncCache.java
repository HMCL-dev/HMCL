// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.property.ObjectProperty;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/// Cache for a property of an item of [javafx.scene.control.ListCell].
///
/// @param <T> type of the property value
/// @param <B> type of the bean, aka. the item
public abstract class ItemPropertyAsyncCache<T, B> {

    private final B bean;

    public ItemPropertyAsyncCache(B bean) {
        this.bean = Objects.requireNonNull(bean);
    }

    protected abstract @Nullable T getValue();

    protected abstract @Nullable T getDefault();

    /// @return the completable future that loads the value, or null if not set or already garbage-collected
    @Nullable
    protected abstract CompletableFuture<T> getFuture();

    protected abstract void setFuture(@NotNull CompletableFuture<T> imageFuture);

    public final void attachValue(ObjectProperty<T> property, @Nullable WeakReference<ObjectProperty<B>> current) {
        CompletableFuture<T> future = getFuture();
        if (future != null) {
            T value = future.getNow(null);
            if (value != null) {
                property.set(value);
                return;
            }
        } else {
            future = CompletableFuture.supplyAsync(this::getValue, Schedulers.io());
            setFuture(future);
        }
        property.set(getDefault());
        future.thenAcceptAsync(image -> {
            if (current != null) {
                ObjectProperty<B> thisProperty = current.get();
                if (thisProperty == null || thisProperty.get() != bean) {
                    // The current ListCell has already switched to another object
                    return;
                }
            }

            property.set(image);
        }, Schedulers.javafx());
    }

    ///
    /// @param <T> {@inheritDoc}
    /// @param <B> {@inheritDoc}
    private static abstract class Base<T, B> extends ItemPropertyAsyncCache<T, B> {

        private final Supplier<T> valueSupplier, defaultSupplier;

        ///
        /// @param bean the item of list cell
        /// @param valueSupplier supplier of the value
        /// @param defaultSupplier supplier of the placeholder value
        public Base(B bean, Supplier<T> valueSupplier, @Nullable Supplier<T> defaultSupplier) {
            super(bean);
            this.valueSupplier = Objects.requireNonNull(valueSupplier);
            this.defaultSupplier = Objects.requireNonNullElse(defaultSupplier, () -> null);
        }

        @Override
        protected @Nullable T getValue() {
            return valueSupplier.get();
        }

        @Override
        protected @Nullable T getDefault() {
            return defaultSupplier.get();
        }
    }

    /// Implementation of [ItemPropertyAsyncCache], using a [SoftReference] to balance the cost of time and memory.
    ///
    /// @param <T> {@inheritDoc}
    /// @param <B> {@inheritDoc}
    public static final class Soft<T, B> extends Base<T, B> {

        private SoftReference<@Nullable CompletableFuture<T>> cache = null;

        ///
        /// @param bean the item of list cell
        /// @param valueSupplier supplier of the value
        /// @param defaultSupplier supplier of the placeholder value
        public Soft(B bean, Supplier<T> valueSupplier, @Nullable Supplier<T> defaultSupplier) {
            super(bean, valueSupplier, defaultSupplier);
        }

        @Override
        protected @Nullable CompletableFuture<T> getFuture() {
            return cache != null ? cache.get() : null;
        }

        @Override
        protected void setFuture(@NotNull CompletableFuture<T> future) {
            this.cache = new SoftReference<>(future);
        }
    }
}
