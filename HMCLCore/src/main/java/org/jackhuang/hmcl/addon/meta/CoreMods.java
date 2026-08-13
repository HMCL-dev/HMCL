package org.jackhuang.hmcl.addon.meta;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @see <a href="https://github.com/xfl03/CoreModTutor">CoreModTutor by xfl03 and contributors</a>
@Immutable
public final class CoreMods {

    public static final CoreMods EMPTY = new CoreMods();

    private final Map<ModLoaderType, List<VersionRange<GameVersionNumber>>> coreMods;

    private CoreMods() {
        this.coreMods = Collections.emptyMap();
    }

    private CoreMods(List<CoreMod> coreModList) {
        EnumMap<ModLoaderType, List<VersionRange<GameVersionNumber>>> map = new EnumMap<>(ModLoaderType.class);
        for (var coreMod : coreModList) {
            map.computeIfAbsent(coreMod.modLoaderType(), k -> new ArrayList<>()).add(coreMod.mcVersionRange());
        }
        this.coreMods = Collections.unmodifiableMap(map);
    }

    public boolean isEmpty() {
        return coreMods.isEmpty();
    }

    public Set<ModLoaderType> getModLoaders(GameVersionNumber gameVersionNumber) {
        EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);
        if (gameVersionNumber == GameVersionNumber.unknown()) return supportedLoaders;
        for (var entry : coreMods.entrySet())
            if (entry.getValue().stream().anyMatch(r -> r.contains(gameVersionNumber)))
                supportedLoaders.add(entry.getKey());
        return supportedLoaders;
    }

    @NotNull
    public static CoreMods fromFile(Path modFile, ZipFileTree tree) {
        if (!"jar".equalsIgnoreCase(FileUtils.getExtension(modFile))) return EMPTY;
        List<CoreMod> coreModList = new ArrayList<>();
        {
            // Below 1.13
            ZipArchiveEntry mf = tree.getEntry("META-INF/MANIFEST.MF");
            if (mf != null) {
                Attributes attr = null;
                try (var in = tree.getInputStream(mf)) {
                    attr = new Manifest(in).getMainAttributes();
                } catch (IOException e) {
                    LOG.warning("Failed to load jar manifest for jar: " + modFile, e);
                }
                if (attr != null) {
                    String fmlCorePlg = attr.getValue("FMLCorePlugin");
                    String tweakClass = attr.getValue("TweakClass");
                    if (StringUtils.isNotBlank(fmlCorePlg))
                        coreModList.addAll(List.of(
                                new CoreMod(ModLoaderType.FORGE, "1.3.2", "1.12.2"),
                                new CoreMod(ModLoaderType.CLEANROOM, "1.12.2", "1.12.2") // TODO further testing
                        ));
                    if (StringUtils.isNotBlank(tweakClass))
                        coreModList.addAll(List.of(
                                new CoreMod(ModLoaderType.FORGE, "1.6.1", "1.12.2"),
                                new CoreMod(ModLoaderType.LITE_LOADER, "1.6.1", "1.12.2"),
                                new CoreMod(ModLoaderType.CLEANROOM, "1.12.2", "1.12.2")
                        ));
                }
            }
        }
        {
            // coremods.json in Forge 1.13-1.21.10 & NeoForge 1.21.4-
            // TODO Find a sample
            ZipArchiveEntry coreModsJson = tree.getEntry("META-INF/coremods.json");
            if (coreModsJson != null) {
                try (var in = new InputStreamReader(tree.getInputStream(coreModsJson))) {
                    var map = JsonUtils.fromJson(in, new TypeToken<Map<String, String>>() {
                    });
                    if (map != null && !map.isEmpty())
                        coreModList.addAll(List.of(
                                // Removed in https://github.com/MinecraftForge/MinecraftForge/pull/10746
                                new CoreMod(ModLoaderType.FORGE, "1.13", "1.21.10"),
                                // Removed in https://github.com/neoforged/NeoForge/pull/2072
                                new CoreMod(ModLoaderType.NEO_FORGE, "1.20.1", "1.21.4")
                        ));
                } catch (IOException | JsonIOException e) {
                    LOG.warning("Failed to read coremods.json for jar: " + modFile, e);
                } catch (JsonSyntaxException ignored) {
                }
            }
        }
        {
            // ITransformationService
            if (tree.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService") != null)
                coreModList.addAll(List.of(
                        new CoreMod(ModLoaderType.FORGE, "1.13.2"),
                        new CoreMod(ModLoaderType.NEO_FORGE, "1.20.1", "1.21.8") // Replaced by ClassProcessorProvider
                ));
        }
        {
            // ICoreMod for NeoForge
            // https://github.com/neoforged/FancyModLoader/pull/79
            // https://neoforged.net/news/2024-retrospection/#the-changes
            // TODO Find a sample
            if (tree.getEntry("META-INF/services/net.neoforged.neoforgespi.coremod.ICoreMod") != null)
                coreModList.add(new CoreMod(ModLoaderType.NEO_FORGE, "1.20.5", "1.21.8")); // Replaced by ClassProcessorProvider
        }
        {
            // ClassProcessorProvider for NeoForge, replaces ITransformationService & ICoreMod
            // https://github.com/neoforged/NeoForge/pull/2655
            // https://github.com/neoforged/FancyModLoader/pull/358
            if (tree.getEntry("META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider") != null)
                coreModList.add(new CoreMod(ModLoaderType.NEO_FORGE, "1.21.9"));
        }
        return new CoreMods(coreModList);
    }

    private record CoreMod(ModLoaderType modLoaderType, VersionRange<GameVersionNumber> mcVersionRange) {

        private CoreMod(ModLoaderType modLoaderType, String min) {
            this(modLoaderType, GameVersionNumber.atLeast(min));
        }

        private CoreMod(ModLoaderType modLoaderType, String min, String max) {
            this(modLoaderType, GameVersionNumber.between(min, max));
        }

    }
}
