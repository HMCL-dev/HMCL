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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
@NotNullByDefault
public enum GameComponentType {
    /// Minecraft itself is never identified from a library coordinate; the game version is supplied
    /// separately to [GameComponentAnalyzer].
    GAME("game") {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return false;
        }
    },
    LEGACY_FABRIC("legacyfabric", ModLoaderType.LEGACY_FABRIC) {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            if (library.is("net.fabricmc", "fabric-loader")) {
                for (Library l : libraries) {
                    if ("net.legacyfabric".equals(l.groupId())) {
                        return true;
                    }
                }
            }
            return false;
        }
    },
    LEGACY_FABRIC_API("legacyfabric-api") {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("net.legacyfabric", "legacyfabric-api");
        }
    },
    FABRIC("fabric", ModLoaderType.FABRIC) {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            if (library.is("net.fabricmc", "fabric-loader")) {
                for (Library l : libraries) {
                    if ("net.legacyfabric".equals(l.groupId())) {
                        return false;
                    }
                }

                return true;
            }

            return false;
        }
    },
    FABRIC_API("fabric-api") {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("net.fabricmc", "fabric-api");
        }
    },
    FORGE("forge", ModLoaderType.FORGE) {
        private final Pattern FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");
        private final Set<String> ARTIFACTS = Set.of("forge", "fmlloader", "minecraftforge");

        @Override
        protected @Nullable String getComponentVersion(GameInstanceManifest manifest, String libraryVersion) {
            Matcher matcher = FORGE_VERSION_MATCHER.matcher(libraryVersion);
            if (matcher.find()) {
                return matcher.group("forge");
            }
            return super.getComponentVersion(manifest, libraryVersion);
        }

        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            if ("net.minecraftforge".equals(library.groupId()) && ARTIFACTS.contains(library.artifactId())) {
                for (Library l : libraries) {
                    if (NEO_FORGE.matchLibrary(l, libraries)) {
                        return false;
                    }
                }

                return true;
            }

            return false;
        }
    },
    CLEANROOM("cleanroom", ModLoaderType.CLEANROOM) {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("com.cleanroommc", "cleanroom");
        }
    },
    NEO_FORGE("neoforge", ModLoaderType.NEO_FORGE) {
        private final Pattern NEO_FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");

        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return "net.neoforged.fancymodloader".equals(library.groupId()) && ("core".equals(library.artifactId()) || "loader".equals(library.artifactId()));
        }

        @Override
        protected @Nullable String getComponentVersion(GameInstanceManifest manifest, String libraryVersion) {
            String res = scanVersion(manifest);
            if (res != null) {
                return res;
            }

            for (GameInstancePatch patch : manifest.getPatches()) {
                res = scanPatch(patch);
                if (res != null) {
                    return res;
                }
            }

            Matcher matcher = NEO_FORGE_VERSION_MATCHER.matcher(libraryVersion);
            if (matcher.find()) {
                return matcher.group("forge");
            }

            return libraryVersion;
        }

        private @Nullable String scanVersion(GameInstanceManifest manifest) {
            if (manifest.arguments() == null) {
                return null;
            }
            List<Argument> gameArguments = manifest.arguments().game();
            if (gameArguments == null) {
                return null;
            }

            for (int i = 0; i < gameArguments.size() - 1; i++) {
                Argument argument = gameArguments.get(i);
                if (argument instanceof StringArgument) {
                    String argumentValue = ((StringArgument) argument).argument();
                    if ("--fml.neoForgeVersion".equals(argumentValue) || "--fml.forgeVersion".equals(argumentValue)) {
                        Argument next = gameArguments.get(i + 1);
                        if (next instanceof StringArgument) {
                            return ((StringArgument) next).argument();
                        }
                        return null; // Normally, there should not be two --fml.neoForgeVersion argument.
                    }
                }
            }
            return null;
        }

        private @Nullable String scanPatch(GameInstancePatch patch) {
            Arguments optArgument = patch.arguments();
            if (optArgument == null) {
                return null;
            }
            List<Argument> gameArguments = optArgument.game();
            if (gameArguments == null) {
                return null;
            }

            for (int i = 0; i < gameArguments.size() - 1; i++) {
                Argument argument = gameArguments.get(i);
                if (argument instanceof StringArgument) {
                    String argumentValue = ((StringArgument) argument).argument();
                    if ("--fml.neoForgeVersion".equals(argumentValue) || "--fml.forgeVersion".equals(argumentValue)) {
                        Argument next = gameArguments.get(i + 1);
                        if (next instanceof StringArgument) {
                            return ((StringArgument) next).argument();
                        }
                        return null;
                    }
                }
            }
            return null;
        }

    },
    LITELOADER("liteloader", ModLoaderType.LITE_LOADER) {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("com.mumfrey", "liteloader");
        }
    },
    OPTIFINE("optifine") {
        private static final Set<String> GROUPS = Set.of("net.optifine", "optifine");

        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return GROUPS.contains(library.groupId()) && !library.artifactId().contains("launchwrapper");
        }
    },
    QUILT("quilt", ModLoaderType.QUILT) {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("org.quiltmc", "quilt-loader");
        }
    },
    QUILT_API("quilt-api") {
        @Override
        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return library.is("org.quiltmc", "quilt-api");
        }
    },
    ;

    public static final List<GameComponentType> ALL = List.of(GameComponentType.values());
    public static final List<GameComponentType> MOD_LOADERS = ALL.stream()
            .filter(GameComponentType::isModLoader)
            .toList();

    private final String patchId;
    private final @Nullable ModLoaderType modLoaderType;

    private static final Map<String, GameComponentType> PATCH_ID_MAP = new HashMap<>();

    static {
        for (GameComponentType type : values()) {
            PATCH_ID_MAP.put(type.getPatchId(), type);
        }
    }

    GameComponentType(String patchId) {
        this.patchId = patchId;
        this.modLoaderType = null;
    }

    GameComponentType(String patchId, ModLoaderType modLoaderType) {
        this.patchId = patchId;
        this.modLoaderType = modLoaderType;
    }

    public boolean isModLoader() {
        return modLoaderType != null;
    }

    @Contract(pure = true)
    public String getPatchId() {
        return patchId;
    }

    public @Nullable ModLoaderType getModLoaderType() {
        return modLoaderType;
    }

    public static @Nullable GameComponentType fromPatchId(String patchId) {
        return PATCH_ID_MAP.get(patchId);
    }

    protected abstract boolean matchLibrary(Library library, List<Library> libraries);

    protected @Nullable String getComponentVersion(GameInstanceManifest manifest, String libraryVersion) {
        return libraryVersion;
    }

}
