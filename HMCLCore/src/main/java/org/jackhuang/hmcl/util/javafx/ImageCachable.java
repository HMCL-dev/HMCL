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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.property.ObjectProperty;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

/// Interface that caches an image, implemented by **items** of [javafx.scene.control.ListCell].
///
///  @param <T> this type
public interface ImageCachable<T> {

    /// @return the completable future that loads the image, or null if not set or already garbage-collected
    @Nullable
    CompletableFuture<Image> getImageFuture();

    void setImageFuture(@NotNull CompletableFuture<Image> imageFuture);

    Image loadImage();

    Image getDefaultImage();

    default void attachImage(ObjectProperty<Image> imageProperty, @Nullable WeakReference<ObjectProperty<T>> current) {
        CompletableFuture<Image> imageFuture = getImageFuture();
        if (imageFuture != null) {
            Image image = imageFuture.getNow(null);
            if (image != null) {
                imageProperty.set(image);
                return;
            }
        } else {
            imageFuture = CompletableFuture.supplyAsync(this::loadImage, Schedulers.io());
            setImageFuture(imageFuture);
        }
        imageProperty.set(getDefaultImage());
        imageFuture.thenAcceptAsync(image -> {
            if (current != null) {
                ObjectProperty<T> thisProperty = current.get();
                if (thisProperty == null || thisProperty.get() != this) {
                    // The current ListCell has already switched to another object
                    return;
                }
            }

            imageProperty.set(image);
        }, Schedulers.javafx());
    }

    /// Implementation of [ImageCachable] using a soft reference to cache the image future,
    /// in order to balance the cost of time and memory.
    abstract class Soft<T> implements ImageCachable<T> {

        private SoftReference<@Nullable CompletableFuture<Image>> cache = null;

        @Override
        public @Nullable CompletableFuture<Image> getImageFuture() {
            return cache != null ? cache.get() : null;
        }

        @Override
        public void setImageFuture(@NotNull CompletableFuture<Image> imageFuture) {
            this.cache = new SoftReference<>(imageFuture);
        }
    }
}
