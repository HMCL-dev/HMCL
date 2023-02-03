/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CrashReportAnalyzerTest {
    private String loadLog(String path) throws IOException {
        List<Pair<String, Log4jLevel>> logs = new ArrayList<>();
        InputStream is = CrashReportAnalyzerTest.class.getResourceAsStream(path);
        if (is == null) {
            throw new IllegalStateException("Resource not found: " + path);
        }
        return IOUtils.readFullyAsString(is);
    }

    private CrashReportAnalyzer.Result findResultByRule(List<CrashReportAnalyzer.Result> results, CrashReportAnalyzer.Rule rule) {
        CrashReportAnalyzer.Result r = results.stream().filter(result -> result.getRule() == rule).findFirst().orElse(null);
        assertNotNull(r);
        return r;
    }

    @Test
    public void jdk9() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/java9.txt")),
                CrashReportAnalyzer.Rule.JDK_9);
    }

    @Test
    public void jvm32() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/jvm_32bit.txt")),
                CrashReportAnalyzer.Rule.JVM_32BIT);
    }

    @Test
    public void jvm321() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/jvm_32bit2.txt")),
                CrashReportAnalyzer.Rule.JVM_32BIT);
    }

    @Test
    public void modResolution() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/mod_resolution.txt")),
                CrashReportAnalyzer.Rule.MOD_RESOLUTION);
        assertEquals(("Errors were found!\n" +
                        " - Mod test depends on mod {fabricloader @ [>=0.11.3]}, which is missing!\n" +
                        " - Mod test depends on mod {fabric @ [*]}, which is missing!\n" +
                        " - Mod test depends on mod {java @ [>=16]}, which is missing!\n").replaceAll("\\s+", ""),
                result.getMatcher().group("reason").replaceAll("\\s+", ""));
    }

    @Test
    public void modResolutionCollection() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/mod_resolution_collection.txt")),
                CrashReportAnalyzer.Rule.MOD_RESOLUTION_COLLECTION);
        assertEquals("tabtps-fabric", result.getMatcher().group("sourcemod"));
        assertEquals("{fabricloader @ [>=0.11.1]}", result.getMatcher().group("destmod"));
    }

    @Test
    public void tooOldJava() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/too_old_java.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        assertEquals("60", result.getMatcher().group("expected"));
    }

    @Test
    public void tooOldJava1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/too_old_java.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        assertEquals("52", result.getMatcher().group("expected"));
    }

    @Test
    public void tooOldJava2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/too_old_java2.txt")),
                CrashReportAnalyzer.Rule.TOO_OLD_JAVA);
        assertEquals("52", result.getMatcher().group("expected"));
    }

    @Test
    public void securityException() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/security.txt")),
                CrashReportAnalyzer.Rule.FILE_CHANGED);
        assertEquals("assets/minecraft/texts/splashes.txt", result.getMatcher().group("file"));
    }

    @Test
    public void noClassDefFoundError1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/no_class_def_found_error.txt")),
                CrashReportAnalyzer.Rule.NO_CLASS_DEF_FOUND_ERROR);
        assertEquals("blk", result.getMatcher().group("class"));
    }

    @Test
    public void noClassDefFoundError2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/no_class_def_found_error2.txt")),
                CrashReportAnalyzer.Rule.NO_CLASS_DEF_FOUND_ERROR);
        assertEquals("cer", result.getMatcher().group("class"));
    }

    @Test
    public void fileAlreadyExists() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/file_already_exists.txt")),
                CrashReportAnalyzer.Rule.FILE_ALREADY_EXISTS);
        assertEquals(
                "D:\\Games\\Minecraft\\Minecraft Longtimeusing\\.minecraft\\versions\\1.12.2-forge1.12.2-14.23.5.2775\\config\\pvpsettings.txt",
                result.getMatcher().group("file"));
    }

    @Test
    public void loaderExceptionModCrash() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED_FORGE);
        assertEquals("Better PvP", result.getMatcher().group("name"));
        assertEquals("xaerobetterpvp", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash2() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash2.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED_FORGE);
        assertEquals("Inventory Sort", result.getMatcher().group("name"));
        assertEquals("invsort", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash3() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash3.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED_FORGE);
        assertEquals("SuperOres", result.getMatcher().group("name"));
        assertEquals("superores", result.getMatcher().group("id"));
    }

    @Test
    public void loaderExceptionModCrash4() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loader_exception_mod_crash4.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED_FORGE);
        assertEquals("Kathairis", result.getMatcher().group("name"));
        assertEquals("kathairis", result.getMatcher().group("id"));
    }

    @Test
    public void loadingErrorFabric() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/loading_error_fabric.txt")),
                CrashReportAnalyzer.Rule.LOADING_CRASHED_FABRIC);
        assertEquals("test", result.getMatcher().group("id"));
    }

    @Test
    public void graphicsDriver() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/graphics_driver.txt")),
                CrashReportAnalyzer.Rule.GRAPHICS_DRIVER);
    }

    @Test
    public void graphicsDriverJVM() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/graphics_driver.txt")),
                CrashReportAnalyzer.Rule.GRAPHICS_DRIVER);
    }

    @Test
    public void splashScreen() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/splashscreen.txt")),
                CrashReportAnalyzer.Rule.GRAPHICS_DRIVER);
    }

    @Test
    public void openj9() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/openj9.txt")),
                CrashReportAnalyzer.Rule.OPENJ9);
    }

    @Test
    public void resolutionTooHigh() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/resourcepack_resolution.txt")),
                CrashReportAnalyzer.Rule.RESOLUTION_TOO_HIGH);
    }

    @Test
    public void bootstrapFailed() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/bootstrap.txt")),
                CrashReportAnalyzer.Rule.BOOTSTRAP_FAILED);
        assertEquals("prefab", result.getMatcher().group("id"));
    }

    @Test
    public void unsatisfiedLinkError() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/unsatisfied_link_error.txt")),
                CrashReportAnalyzer.Rule.UNSATISFIED_LINK_ERROR);
        assertEquals("lwjgl.dll", result.getMatcher().group("name"));
    }

    @Test
    public void outOfMemoryMC() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/out_of_memory.txt")),
                CrashReportAnalyzer.Rule.OUT_OF_MEMORY);
    }

    @Test
    public void outOfMemoryJVM() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/out_of_memory.txt")),
                CrashReportAnalyzer.Rule.OUT_OF_MEMORY);
    }

    @Test
    public void memoryExceeded() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/memory_exceeded.txt")),
                CrashReportAnalyzer.Rule.MEMORY_EXCEEDED);
    }

    @Test
    public void config() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/config.txt")),
                CrashReportAnalyzer.Rule.CONFIG);
        assertEquals("jumbofurnace", result.getMatcher().group("id"));
        assertEquals("jumbofurnace-server.toml", result.getMatcher().group("file"));
    }

    @Test
    public void fabricWarnings() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric_warnings.txt")),
                CrashReportAnalyzer.Rule.FABRIC_WARNINGS);
        assertEquals((" - Conflicting versions found for fabric-api-base: used 0.3.0+a02b446313, also found 0.3.0+a02b44633d, 0.3.0+a02b446318\n" +
                        " - Conflicting versions found for fabric-rendering-data-attachment-v1: used 0.1.5+a02b446313, also found 0.1.5+a02b446318\n" +
                        " - Conflicting versions found for fabric-rendering-fluids-v1: used 0.1.13+a02b446318, also found 0.1.13+a02b446313\n" +
                        " - Conflicting versions found for fabric-lifecycle-events-v1: used 1.4.4+a02b44633d, also found 1.4.4+a02b446318\n" +
                        " - Mod 'Sodium Extra' (sodium-extra) recommends any version of mod reeses-sodium-options, which is missing!\n" +
                        "\t - You must install any version of reeses-sodium-options.\n" +
                        " - Conflicting versions found for fabric-screen-api-v1: used 1.0.4+155f865c18, also found 1.0.4+198a96213d\n" +
                        " - Conflicting versions found for fabric-key-binding-api-v1: used 1.0.4+a02b446318, also found 1.0.4+a02b44633d\n").replaceAll("\\s+", ""),
                result.getMatcher().group("reason").replaceAll("\\s+", ""));
    }

    @Test
    public void fabricWarnings1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric_warnings2.txt")),
                CrashReportAnalyzer.Rule.FABRIC_WARNINGS);
        assertEquals(("net.fabricmc.loader.impl.FormattedException: Mod resolution encountered an incompatible mod set!\n" +
                        "A potential solution has been determined:\n" +
                        "\t - Install roughlyenoughitems, version 6.0.2 or later.\n" +
                        "Unmet dependency listing:\n" +
                        "\t - Mod 'Roughly Searchable' (roughlysearchable) 2.2.1+1.17.1 requires version 6.0.2 or later of roughlyenoughitems, which is missing!\n" +
                        "\tat net.fabricmc.loader.impl.FabricLoaderImpl.load(FabricLoaderImpl.java:190) ~").replaceAll("\\s+", ""),
                result.getMatcher().group("reason").replaceAll("\\s+", ""));
    }

    @Test
    public void fabricConflicts() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric-mod-conflict.txt")),
                CrashReportAnalyzer.Rule.MOD_RESOLUTION_CONFLICT);
        assertEquals("phosphor", result.getMatcher().group("sourcemod"));
        assertEquals("{starlight @ [*]}", result.getMatcher().group("destmod"));
    }

    @Test
    public void fabricMissing() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric-mod-missing.txt")),
                CrashReportAnalyzer.Rule.MOD_RESOLUTION_MISSING);
        assertEquals("pca", result.getMatcher().group("sourcemod"));
        assertEquals("{fabric @ [>=0.39.2]}", result.getMatcher().group("destmod"));
    }

    @Test
    public void fabric0_12() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric-version-0.12.txt")),
                CrashReportAnalyzer.Rule.FABRIC_VERSION_0_12);
    }

    @Test
    public void twilightForestOptiFineIncompatible() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/mod/twilightforest_optifine_incompatibility.txt")),
                CrashReportAnalyzer.Rule.TWILIGHT_FOREST_OPTIFINE);
    }

    @Test
    public void fabricMissingMinecraft() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/fabric-minecraft.txt")),
                CrashReportAnalyzer.Rule.MOD_RESOLUTION_MISSING_MINECRAFT);
        assertEquals("fabric", result.getMatcher().group("mod"));
        assertEquals("[~1.16.2-alpha.20.28.a]", result.getMatcher().group("version"));
    }

    @Test
    public void optifineRepeatInstallation() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/optifine_repeat_installation.txt")),
                CrashReportAnalyzer.Rule.OPTIFINE_REPEAT_INSTALLATION);
    }

    @Test
    public void forgeRepeatInstallation() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/forge_repeat_installation.txt")),
                CrashReportAnalyzer.Rule.FORGE_REPEAT_INSTALLATION);
    }

    @Test
    public void optifineIsNotCompatibleWithForge() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/optifine_is_not_compatible_with_forge.txt")),
                CrashReportAnalyzer.Rule.OPTIFINE_IS_NOT_COMPATIBLE_WITH_FORGE);
    }

    @Test
    public void optifineIsNotCompatibleWithForge1() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/logs/optifine_is_not_compatible_with_forge2.txt")),
                CrashReportAnalyzer.Rule.OPTIFINE_IS_NOT_COMPATIBLE_WITH_FORGE);
    }

    @Test
    public void customNpc() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/mod/customnpc.txt")),
                CrashReportAnalyzer.Rule.ENTITY);
        assertEquals("customnpcs.CustomNpc (noppes.npcs.entity.EntityCustomNpc)",
                result.getMatcher().group("type"));
        assertEquals("99942.59, 4.00, 100000.98",
                result.getMatcher().group("location"));

        assertEquals(
                new HashSet<>(Arrays.asList("npcs", "noppes")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/customnpc.txt")));
    }

    @Test
    public void tconstruct() throws IOException {
        CrashReportAnalyzer.Result result = findResultByRule(
                CrashReportAnalyzer.anaylze(loadLog("/crash-report/mod/tconstruct.txt")),
                CrashReportAnalyzer.Rule.BLOCK);
        assertEquals("Block{tconstruct:seared_drain}[active=true,facing=north]",
                result.getMatcher().group("type"));
        assertEquals("World: (1370,92,-738), Chunk: (at 10,5,14 in 85,-47; contains blocks 1360,0,-752 to 1375,255,-737), Region: (2,-2; contains chunks 64,-64 to 95,-33, blocks 1024,0,-1024 to 1535,255,-513)",
                result.getMatcher().group("location"));

        assertEquals(
                new HashSet<>(Arrays.asList("tconstruct", "slimeknights", "smeltery")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/tconstruct.txt")));
    }

    @Test
    public void bettersprinting() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("chylex", "bettersprinting")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/bettersprinting.txt")));
    }

    @Test
    public void ic2() throws IOException {
        assertEquals(
                Collections.singleton("ic2"),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/ic2.txt")));
    }

    @Test
    public void nei() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("nei", "codechicken", "guihook")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/nei.txt")));
    }

    @Test
    public void netease() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("netease", "battergaming")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/netease.txt")));
    }

    @Test
    public void flammpfeil() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("slashblade", "flammpfeil")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/flammpfeil.txt")));
    }

    @Test
    public void creativemd() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("creativemd", "itemphysic")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/creativemd.txt")));
    }

    @Test
    public void mapletree() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("MapleTree", "bamboo", "uraniummc", "ecru")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/mapletree.txt")));
    }

    @Test
    public void thaumcraft() throws IOException {
        assertEquals(
                Collections.singleton("thaumcraft"),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/thaumcraft.txt")));
    }

    @Test
    public void shadersmodcore() throws IOException {
        assertEquals(
                Collections.singleton("shadersmodcore"),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/shadersmodcore.txt")));
    }

    @Test
    public void twilightforest() throws IOException {
        assertEquals(
                Collections.singleton("twilightforest"),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/twilightforest.txt")));
    }

    @Test
    public void optifine() throws IOException {
        assertEquals(
                Collections.singleton("OptiFine"),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/twilightforest_optifine_incompatibility.txt")));
    }

    @Test
    public void wizardry() throws IOException {
        assertEquals(
                new HashSet<>(Arrays.asList("wizardry", "electroblob", "projectile")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/wizardry.txt")));
    }

    @Test
    public void icycream() throws IOException {
        assertEquals(
                new HashSet<>(Collections.singletonList("icycream")),
                CrashReportAnalyzer.findKeywordsFromCrashReport(loadLog("/crash-report/mod/icycream.txt")));
    }
}
