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
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

/// @param <T> this type
public interface IconCachable<T> {

    @Nullable
    SoftReference<@Nullable CompletableFuture<Image>> getIconCache();

    void setIconCache(@NotNull SoftReference<CompletableFuture<Image>> iconCache);

    Image loadIcon();

    Image getDefaultIcon();

    default void attachIcon(ImageContainer imageContainer, @Nullable WeakReference<ObjectProperty<T>> current) {
        SoftReference<CompletableFuture<Image>> iconCache = getIconCache();
        CompletableFuture<Image> imageFuture;
        if (iconCache != null && (imageFuture = iconCache.get()) != null) {
            Image image = imageFuture.getNow(null);
            if (image != null) {
                imageContainer.setImage(image);
                return;
            }
        } else {
            imageFuture = CompletableFuture.supplyAsync(this::loadIcon, Schedulers.io());
            setIconCache(new SoftReference<>(imageFuture));
        }
        imageContainer.setImage(getDefaultIcon());
        imageFuture.thenAcceptAsync(image -> {
            if (current != null) {
                ObjectProperty<T> thisProperty = current.get();
                if (thisProperty == null || thisProperty.get() != this) {
                    // The current ListCell has already switched to another object
                    return;
                }
            }

            imageContainer.setImage(image);
        }, Schedulers.javafx());
    }

}
