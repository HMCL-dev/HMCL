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
package org.jackhuang.hmcl.addon;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Favorites {

    private static final TypeToken<LinkedHashSet<Item>> typeToken = new TypeToken<>() {
    };

    private final Path file; // Any external changes to this file might be lost
    private final LinkedHashSet<Item> items = new LinkedHashSet<>();
    private final List<RemoteAddon> addons = new ArrayList<>();
    private final HashMap<Item, RemoteAddon> cache = new HashMap<>();
    private DownloadProvider lastProvider = null;

    private final ReentrantLock lock = new ReentrantLock();

    public Favorites(Path favoritesFile) {
        this.file = Objects.requireNonNull(favoritesFile);
    }

    public Path getFile() {
        return file;
    }

    public void load() {
        lock.lock();
        try {
            items.clear();
            var set = JsonUtils.fromJsonFile(file, typeToken);
            if (set != null) items.addAll(set);
        } catch (IOException | JsonSyntaxException e) {
            LOG.warning("Failed to load favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    public void save() {
        lock.lock();
        try {
            JsonUtils.writeToJsonFile(file, items);
        } catch (IOException e) {
            LOG.warning("Failed to save favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    public void add(RemoteAddon addon) {
        lock.lock();
        try {
            var item = Item.fromAddon(addon);
            items.remove(item);
            items.add(item);
        } finally {
            lock.unlock();
        }
    }

    /// @return Whether the items list was modified.
    public boolean remove(Collection<RemoteAddon> addons) {
        lock.lock();
        try {
            return items.removeAll(addons.stream().map(Item::fromAddon).collect(Collectors.toSet()));
        } finally {
            lock.unlock();
        }
    }

    public void resolveAll(DownloadProvider downloadProvider) {
        lock.lock();
        try {
            addons.clear();
            if (downloadProvider != lastProvider) {
                cache.clear();
                lastProvider = downloadProvider;
            }
            LinkedHashMap<RemoteAddon, Item> resultReversed = new LinkedHashMap<>(items.size());
            for (var item : Lang.reversedCopyOf(items)) {
                RemoteAddon addon;
                if (cache.containsKey(item)) {
                    addon = cache.get(item);
                } else {
                    try {
                        addon = item.resolve(downloadProvider);
                    } catch (IOException e) {
                        LOG.warning("Failed to resolve favorite item: " + item, e);
                        continue;
                    }
                }
                if (!resultReversed.containsKey(addon)) {
                    resultReversed.put(addon, item);
                    cache.put(item, addon);
                }
            }
            addons.addAll(Lang.reversedCopyOf(resultReversed.keySet()));
            if (items.retainAll(resultReversed.values())) { // Remove duplicate items
                save();
            }
        } finally {
            lock.unlock();
        }
    }

    @JsonSerializable
    public record Item(@Nullable String modId, @Nullable RemoteAddon.Source source) {

        public static Item fromAddon(RemoteAddon addon) {
            return new Item(addon.projectId(), addon.source());
        }

        public @NotNull RemoteAddon resolve(DownloadProvider downloadProvider) throws IOException {
            if (modId == null || source == null) return RemoteAddon.BROKEN;
            var repo = source.getRepoForType(RemoteAddonRepository.Type.MOD); //TODO use common repo
            assert repo != null;
            return repo.getAddonById(downloadProvider, modId);
        }

    }
}
