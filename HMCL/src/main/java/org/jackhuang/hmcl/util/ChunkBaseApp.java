/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;

public final class ChunkBaseApp {
    private static final String CHUNK_BASE_URL = "https://www.chunkbase.com";

    private static final String GAME_VERSIONS_RESOURCE = "/assets/chunkbase/game_versions.json";

    private static final GameVersionNumber MIN_GAME_VERSION = GameVersionNumber.asGameVersion("1.7");
    private static final GameVersionNumber MIN_END_CITY_VERSION = GameVersionNumber.asGameVersion("1.13");

    private static final @NotNull @Unmodifiable Map<String, @Unmodifiable List<String>> GAME_VERSIONS = loadGameVersions();

    public static final String @NotNull @Unmodifiable [] SEED_MAP_GAME_VERSIONS = getGameVersions("seed-map");

    public static final String @NotNull @Unmodifiable [] STRONGHOLD_FINDER_GAME_VERSIONS = getGameVersions("stronghold-finder");

    public static final String @NotNull @Unmodifiable [] NETHER_FORTRESS_GAME_VERSIONS = getGameVersions("nether-fortress");

    public static final String @NotNull @Unmodifiable [] END_CITY_GAME_VERSIONS = getGameVersions("end-city");


    public static boolean isSupported(@NotNull World world) {
        return world.getSeed() != null && world.getGameVersion() != null &&
                world.getGameVersion().compareTo(MIN_GAME_VERSION) >= 0;
    }

    public static boolean supportEndCity(@NotNull World world) {
        return world.getSeed() != null && world.getGameVersion() != null &&
                world.getGameVersion().compareTo(MIN_END_CITY_VERSION) >= 0;
    }

    public static ChunkBaseApp newBuilder(String app, long seed) {
        return new ChunkBaseApp(new StringBuilder(CHUNK_BASE_URL).append("/apps/").append(app).append("#seed=").append(seed));
    }

    public static void openSeedMap(World world) {
        assert isSupported(world);

        newBuilder("seed-map", Objects.requireNonNull(world.getSeed()))
                .addPlatform(world.getGameVersion(), world.isLargeBiomes(), SEED_MAP_GAME_VERSIONS)
                .open();
    }

    public static void openStrongholdFinder(World world) {
        assert isSupported(world);

        newBuilder("stronghold-finder", Objects.requireNonNull(world.getSeed()))
                .addPlatform(world.getGameVersion(), world.isLargeBiomes(), STRONGHOLD_FINDER_GAME_VERSIONS)
                .open();
    }

    public static void openNetherFortressFinder(World world) {
        assert isSupported(world);

        newBuilder("nether-fortress-finder", Objects.requireNonNull(world.getSeed()))
                .addPlatform(world.getGameVersion(), false, NETHER_FORTRESS_GAME_VERSIONS)
                .open();
    }

    public static void openEndCityFinder(World world) {
        assert isSupported(world);

        newBuilder("endcity-finder", Objects.requireNonNull(world.getSeed()))
                .addPlatform(world.getGameVersion(), false, END_CITY_GAME_VERSIONS)
                .open();
    }

    private static @NotNull @Unmodifiable Map<String, @Unmodifiable List<String>> loadGameVersions() {
        try (InputStream in = ChunkBaseApp.class.getResourceAsStream(GAME_VERSIONS_RESOURCE)) {
            Map<String, List<String>> raw =
                    JsonUtils.fromNonNullJsonFully(in, mapTypeOf(String.class, listTypeOf(String.class)));

            Map<String, List<String>> gameVersions = new LinkedHashMap<>(raw.size());
            raw.forEach((key, versions) -> gameVersions.put(key, List.copyOf(versions)));
            return Map.copyOf(gameVersions);
        } catch (IOException e) {
            throw new AssertionError("Failed to load " + GAME_VERSIONS_RESOURCE, e);
        }
    }

    private static String @NotNull @Unmodifiable [] getGameVersions(@NotNull String key) {
        List<String> versions = Objects.requireNonNull(GAME_VERSIONS.get(key),
                "Missing game version list in " + GAME_VERSIONS_RESOURCE + ": " + key);
        return versions.toArray(new String[0]);
    }

    private final StringBuilder builder;

    private ChunkBaseApp(StringBuilder builder) {
        this.builder = builder;
    }

    public ChunkBaseApp add(String key, String value) {
        builder.append('&').append(key).append('=').append(value);
        return this;
    }

    public ChunkBaseApp addPlatform(GameVersionNumber gameVersion, boolean largeBiomes, String[] versionList) {
        String version = null;
        for (String candidateVersion : versionList) {
            if (gameVersion.compareTo(candidateVersion) >= 0) {
                version = candidateVersion;
                break;
            }
        }

        if (version == null) {
            version = versionList[versionList.length - 1]; // Use the last version if no suitable version found
        }

        add("platform", "java_" + version.replace('.', '_') + (largeBiomes ? "_lb" : ""));
        return this;
    }

    public void open() {
        FXUtils.openLink(builder.toString());
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
