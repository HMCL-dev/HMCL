// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.beans.value.WritableValue;
import javafx.scene.image.Image;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public abstract class RemoteImageLoader {
    private final DownloadProvider downloadProvider;
    /// Loaded images indexed by normalized URL; image references do not prevent garbage collection.
    private final Map<WebURL, WeakReference<@Nullable Image>> cache = new HashMap<>();
    /// Image targets awaiting each URL's active load task.
    private final Map<WebURL, List<WeakReference<WritableValue<Image>>>> pendingRequests = new HashMap<>();
    /// Current requested URL for each target, used to discard obsolete load completions.
    private final WeakHashMap<WritableValue<Image>, WebURL> reverseLookup = new WeakHashMap<>();

    public RemoteImageLoader(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    protected @Nullable Image getPlaceholder() {
        return null;
    }

    /// Creates a task that loads an image from candidate URLs in attempt order.
    protected abstract @NotNull Task<Image> createLoadTask(DownloadCandidates candidates);

    /// Loads an image or assigns the placeholder when the URL is absent or invalid.
    @FXThread
    public void load(@NotNull WritableValue<Image> writableValue, @Nullable String url) {
        @Nullable WebURL webUrl = NetworkUtils.toWebURLOrNull(url);
        if (webUrl == null) {
            reverseLookup.remove(writableValue);
            writableValue.setValue(getPlaceholder());
            return;
        }

        @Nullable WeakReference<@Nullable Image> reference = cache.get(webUrl);
        if (reference != null) {
            @Nullable Image image = reference.get();
            if (image != null) {
                reverseLookup.remove(writableValue);
                writableValue.setValue(image);
                return;
            }
            cache.remove(webUrl);
        }

        writableValue.setValue(getPlaceholder());

        {
            @Nullable List<WeakReference<WritableValue<Image>>> list = pendingRequests.get(webUrl);
            if (list != null) {
                list.add(new WeakReference<>(writableValue));
                reverseLookup.put(writableValue, webUrl);
                return;
            } else {
                list = new ArrayList<>(1);
                list.add(new WeakReference<>(writableValue));
                pendingRequests.put(webUrl, list);
                reverseLookup.put(writableValue, webUrl);
            }
        }

        createLoadTask(downloadProvider.getDownloadCandidates(url)).whenComplete(Schedulers.javafx(), (result, exception) -> {
            @Nullable Image image;
            if (exception == null) {
                image = result;
            } else {
                LOG.warning("Failed to load image from " + webUrl, exception);
                image = getPlaceholder();
            }

            cache.put(webUrl, new WeakReference<>(image));
            @Nullable List<WeakReference<WritableValue<Image>>> list = pendingRequests.remove(webUrl);
            if (list != null) {
                for (WeakReference<WritableValue<Image>> ref : list) {
                    @Nullable WritableValue<Image> target = ref.get();
                    if (target != null && webUrl.equals(reverseLookup.get(target))) {
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
