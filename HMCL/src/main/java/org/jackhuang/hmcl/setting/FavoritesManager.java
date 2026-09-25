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
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FavoritesManager {

    private static final TypeToken<TreeMap<String, LinkedHashSet<Item>>> typeToken = new TypeToken<>() {
    };

    private static final FavoritesManager instance = new FavoritesManager(Metadata.HMCL_USER_HOME.resolve("config").resolve("user-addon-favorites.json"));

    public static FavoritesManager getInstance() {
        return instance;
    }

    private final Path file; // Any external changes to this file while the application is running might be lost
    private final TreeMap<String, Favorite> favoritesMap = new TreeMap<>(String::compareToIgnoreCase);
    private final ReentrantLock lock = new ReentrantLock();
    private boolean loaded;

    private FavoritesManager(Path favoritesFile) {
        this.file = Objects.requireNonNull(favoritesFile);
    }

    public Path getFile() {
        return file;
    }

    public void refresh() {
        lock.lock();
        try {
            loaded = false;
            load();
        } finally {
            lock.unlock();
        }
    }

    public void load() {
        if (loaded) return;
        lock.lock();
        try {
            if (loaded) return;
            favoritesMap.clear();
            var map = Files.isRegularFile(file) ? JsonUtils.fromJsonFile(file, typeToken) : null;
            if (map != null)
                map.forEach((name, items) -> favoritesMap.put(name, new Favorite(this, name, items)));
            favoritesMap.computeIfAbsent("", n -> new Favorite(this, n, new LinkedHashSet<>()));
            loaded = true;
        } catch (IOException | JsonSyntaxException e) {
            LOG.warning("Failed to load favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    private void save0() {
        lock.lock();
        try {
            if (!loaded) load();
            var pairs = favoritesMap.entrySet().stream().map(entry -> Pair.pair(entry.getKey(), entry.getValue().items)).toList();
            JsonUtils.writeToJsonFile(file, Lang.mapOf(pairs));
        } catch (IOException e) {
            LOG.warning("Failed to save favorites file at " + file, e);
        } finally {
            lock.unlock();
        }
    }

    public void save() {
        CompletableFuture.runAsync(this::save0, Schedulers.io());
    }

    public void resolveAll(DownloadProvider downloadProvider) {
        lock.lock();
        try {
            favoritesMap.values().forEach(fav -> fav.resolve0(downloadProvider));
        } finally {
            lock.unlock();
        }
    }

    public @Unmodifiable List<Favorite> getFavorites() {
        lock.lock();
        try {
            if (!loaded) throw new IllegalStateException("Favorites not loaded");
            return List.copyOf(favoritesMap.values());
        } finally {
            lock.unlock();
        }
    }

    public @NotNull FavoritesManager.Favorite getOrCreate(String name) {
        lock.lock();
        try {
            return favoritesMap.computeIfAbsent(name, n -> new Favorite(this, n, new LinkedHashSet<>()));
        } finally {
            lock.unlock();
        }
    }

    public @NotNull FavoritesManager.Favorite getDefault() {
        return getOrCreate("");
    }

    public @Nullable FavoritesManager.Favorite get(String name) {
        lock.lock();
        try {
            return favoritesMap.get(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean has(String name) {
        return get(name) != null;
    }

    public boolean contains(RemoteAddon addon) {
        lock.lock();
        try {
            return favoritesMap.values().stream().anyMatch(f -> f.contains(addon));
        } finally {
            lock.unlock();
        }
    }

    public static final class Favorite {

        private final FavoritesManager manager;
        private final String name;

        private final LinkedHashSet<Item> items;
        private transient final LinkedHashMap<Item, RemoteAddon> resolvedAddons = new LinkedHashMap<>();
        private transient DownloadProvider lastProvider = null;

        private final ReentrantLock lock;

        private Favorite(FavoritesManager manager, String name, LinkedHashSet<Item> items) {
            this.manager = manager;
            this.lock = manager.lock;
            this.name = name;
            this.items = items.stream()
                    .filter(item -> item.projectId() != null && item.source() != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        public String getName() {
            return name;
        }

        public boolean isDefault() {
            return "".equals(name);
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

        public @Unmodifiable Map<Item, RemoteAddon> getResolvedAddons() {
            lock.lock();
            try {
                return Collections.unmodifiableMap(new LinkedHashMap<>(resolvedAddons));
            } finally {
                lock.unlock();
            }
        }

        private void resolve0(DownloadProvider downloadProvider) {
            Map<Item, RemoteAddon> cache;
            if (downloadProvider != lastProvider) {
                cache = Map.of();
                lastProvider = downloadProvider;
            } else {
                cache = Map.copyOf(resolvedAddons);
            }
            resolvedAddons.clear();
            Map<Item, RemoteAddon> result = new LinkedHashMap<>(items.size());
            for (var item : items) {
                RemoteAddon addon;
                if (cache.containsKey(item)) {
                    addon = cache.get(item);
                } else {
                    try {
                        addon = item.resolve(downloadProvider);
                    } catch (IOException e) {
                        LOG.warning("Failed to resolve favorite item: " + item, e);
                        result.put(item, null);
                        continue;
                    }
                }
                result.put(item, addon);
            }
            resolvedAddons.putAll(result);
        }

        public void resolve(DownloadProvider downloadProvider) {
            lock.lock();
            try {
                resolve0(downloadProvider);
            } finally {
                lock.unlock();
            }
        }

        public boolean contains(RemoteAddon addon) {
            lock.lock();
            try {
                return items.contains(Item.fromAddon(addon));
            } finally {
                lock.unlock();
            }
        }

        public void add(RemoteAddon addon) {
            lock.lock();
            try {
                items.add(Item.fromAddon(addon));
                manager.save();
            } finally {
                lock.unlock();
            }
        }

        public boolean remove(Collection<Item> itemsToRemove) {
            lock.lock();
            try {
                if (items.removeAll(itemsToRemove)) {
                    manager.save();
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        public boolean removeAddons(Collection<RemoteAddon> addons) {
            return remove(addons.stream().map(Item::fromAddon).collect(Collectors.toSet()));
        }
    }

    @JsonSerializable
    public record Item(@SerializedName("id") String projectId, @SerializedName("src") RemoteAddon.Source source) {

        public static Item fromAddon(RemoteAddon addon) {
            return new Item(addon.projectId(), addon.source());
        }

        public @NotNull RemoteAddon resolve(DownloadProvider downloadProvider) throws IOException {
            if (projectId == null || source == null) return RemoteAddon.BROKEN;
            return source.getCommonRepo().getAddonById(downloadProvider, projectId);
        }
    }
}
