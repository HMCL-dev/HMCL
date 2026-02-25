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
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ChunkBaseApp {
    private static final String CHUNK_BASE_URL = "https://www.chunkbase.com";

    private static final GameVersionNumber MIN_GAME_VERSION = GameVersionNumber.asGameVersion("1.7");
    private static final GameVersionNumber MIN_END_CITY_VERSION = GameVersionNumber.asGameVersion("1.13");

    private static final String[] SEED_MAP_GAME_VERSIONS = {
            "1.21.9", "1.21.6", "1.21.5", "1.21.4", "1.21.2", "1.21", "1.20",
            "1.19.3", "1.19", "1.18", "1.17", "1.16", "1.15", "1.14", "1.13",
            "1.12", "1.11", "1.10", "1.9", "1.8", "1.7"
    };

    public static final String[] STRONGHOLD_FINDER_GAME_VERSIONS = {
            "1.20", "1.19.3", "1.19", "1.18", "1.16", "1.13", "1.9", "1.7"
    };

    public static final String[] NETHER_FORTRESS_GAME_VERSIONS = {
            "1.18", "1.16", "1.7"
    };

    public static final String[] END_CITY_GAME_VERSIONS = {
            "1.19", "1.13"
    };

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
