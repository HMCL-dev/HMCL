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
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FavoritesManager {

    private static final TypeToken<TreeMap<String, LinkedHashSet<Item>>> typeToken = new TypeToken<>() {
    };

    private final Path file; // Any external changes to this file might be lost

    private final TreeMap<String, Favorites> favoritesMap = new TreeMap<>(String::compareToIgnoreCase);

    private final ReentrantLock lock = new ReentrantLock();

    public FavoritesManager(Path favoritesFile) {
        this.file = Objects.requireNonNull(favoritesFile);
    }

    public Path getFile() {
        return file;
    }

    public void load() {
        lock.lock();
        try {
            favoritesMap.clear();
            var map = JsonUtils.fromJsonFile(file, typeToken);
            if (map != null)
                map.forEach((name, items) -> favoritesMap.put(name, new Favorites(this, name, items)));
        } catch (IOException | JsonSyntaxException e) {
            LOG.warning("Failed to load favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    public void save() {
        lock.lock();
        try {
            var pairs = favoritesMap.entrySet().stream().map(entry -> Pair.pair(entry.getKey(), entry.getValue().items)).toList();
            JsonUtils.writeToJsonFile(file, Lang.mapOf(pairs));
        } catch (IOException e) {
            LOG.warning("Failed to save favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    public void resolveAll(DownloadProvider downloadProvider) {
        lock.lock();
        try {
            boolean modified = false;
            for (var fav : favoritesMap.values())
                modified |= fav.resolve0(downloadProvider);
            if (modified)
                save();
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    public Favorites getOrCreate(String name) {
        lock.lock();
        try {
            return favoritesMap.computeIfAbsent(name, n -> new Favorites(this, n, new LinkedHashSet<>()));
        } finally {
            lock.unlock();
        }
    }

    public static final class Favorites {

        private final FavoritesManager manager;
        private final String name;

        private final LinkedHashSet<Item> items;
        private transient final ArrayList<RemoteAddon> resolvedAddons = new ArrayList<>();
        private transient final HashMap<Item, RemoteAddon> cache = new HashMap<>();
        private transient DownloadProvider lastProvider = null;

        private final ReentrantLock lock;

        private Favorites(FavoritesManager manager, String name, LinkedHashSet<Item> items) {
            this.manager = manager;
            this.lock = manager.lock;
            this.name = name;
            this.items = new LinkedHashSet<>(items);
        }

        public String getName() {
            return name;
        }

        @Unmodifiable
        public Set<Item> getItems() {
            lock.lock();
            try {
                return Set.copyOf(items);
            } finally {
                lock.unlock();
            }
        }

        @Unmodifiable
        public List<RemoteAddon> getResolvedAddons() {
            lock.lock();
            try {
                return List.copyOf(resolvedAddons);
            } finally {
                lock.unlock();
            }
        }

        /// @return Whether the item list was modified.
        private boolean resolve0(DownloadProvider downloadProvider) {
            resolvedAddons.clear();
            if (downloadProvider != lastProvider) {
                cache.clear();
                lastProvider = downloadProvider;
            }
            LinkedHashMap<RemoteAddon, Item> resultReversed = new LinkedHashMap<>(items.size());
            for (var item : Lang.reversedCopyOf(items)) { // TODO migrate to SequencedSet::reversed after upgrading to JDK 21+
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
            resolvedAddons.addAll(Lang.reversedCopyOf(resultReversed.keySet()));
            return items.retainAll(resultReversed.values()); // Remove duplicate items
        }

        public void resolve(DownloadProvider downloadProvider) {
            lock.lock();
            try {
                if (resolve0(downloadProvider))
                    manager.save();
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
                manager.save();
            } finally {
                lock.unlock();
            }
        }

        public void remove(Collection<RemoteAddon> addons) {
            lock.lock();
            try {
                if (items.removeAll(addons.stream().map(Item::fromAddon).collect(Collectors.toSet())))
                    manager.save();
            } finally {
                lock.unlock();
            }
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
