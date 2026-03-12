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

import javafx.beans.value.WritableValue;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public abstract class RemoteImageLoader {
    private final Map<URI, WeakReference<Image>> cache = new HashMap<>();
    private final Map<URI, List<WeakReference<WritableValue<Image>>>> pendingRequests = new HashMap<>();
    private final WeakHashMap<WritableValue<Image>, URI> reverseLookup = new WeakHashMap<>();

    public RemoteImageLoader() {
    }

    protected @Nullable Image getPlaceholder() {
        return null;
    }

    protected abstract @NotNull Task<Image> createLoadTask(@NotNull URI uri);

    @FXThread
    public void load(@NotNull WritableValue<Image> writableValue, String url) {
        URI uri = NetworkUtils.toURIOrNull(url);
        if (uri == null) {
            reverseLookup.remove(writableValue);
            writableValue.setValue(getPlaceholder());
            return;
        }

        WeakReference<Image> reference = cache.get(uri);
        if (reference != null) {
            Image image = reference.get();
            if (image != null) {
                reverseLookup.remove(writableValue);
                writableValue.setValue(image);
                return;
            }
            cache.remove(uri);
        }

        writableValue.setValue(getPlaceholder());

        {
            List<WeakReference<WritableValue<Image>>> list = pendingRequests.get(uri);
            if (list != null) {
                list.add(new WeakReference<>(writableValue));
                reverseLookup.put(writableValue, uri);
                return;
            } else {
                list = new ArrayList<>(1);
                list.add(new WeakReference<>(writableValue));
                pendingRequests.put(uri, list);
                reverseLookup.put(writableValue, uri);
            }
        }

        createLoadTask(uri).whenComplete(Schedulers.javafx(), (result, exception) -> {
            Image image;
            if (exception == null) {
                image = result;
            } else {
                LOG.warning("Failed to load image from " + uri, exception);
                image = getPlaceholder();
            }

            cache.put(uri, new WeakReference<>(image));
            List<WeakReference<WritableValue<Image>>> list = pendingRequests.remove(uri);
            if (list != null) {
                for (WeakReference<WritableValue<Image>> ref : list) {
                    WritableValue<Image> target = ref.get();
                    if (target != null && uri.equals(reverseLookup.get(target))) {
                        reverseLookup.remove(target);
                        target.setValue(image);
                    }
                }
            }
        }).start();
    }

    @FXThread
    public void unload(@NotNull WritableValue<Image> writableValue) {
        reverseLookup.remove(writableValue);
    }

    @FXThread
    public void clearInvalidCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }
}
