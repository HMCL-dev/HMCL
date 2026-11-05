// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for game instance manifest parsing and resolution behavior.
@NotNullByDefault
public final class GameInstanceManifestTest {

    /// Only independent modern vanilla manifests with a null patch list qualify without a game patch.
    @Test
    public void testModifiableWithoutGamePatch() {
        GameInstanceManifest vanilla = manifest("example", GameComponentAnalyzer.VANILLA_MAIN, null, null, null);
        assertTrue(vanilla.isModifiable());
        assertFalse(vanilla.withPatches(List.of()).isModifiable());
        assertFalse(vanilla.withInheritsFrom(new GameInstanceID("parent")).isModifiable());
        assertFalse(vanilla.withMainClass(null).isModifiable());
        assertFalse(vanilla.withMainClass("net.minecraft.launchwrapper.Launch").isModifiable());
        assertFalse(vanilla.withMainClass("com.mojang.rubydung.RubyDung").isModifiable());
        assertFalse(vanilla.withMainClass("net.fabricmc.loader.impl.launch.knot.KnotClient").isModifiable());
        assertFalse(vanilla.withMainClass("example.Main").isModifiable());
        assertFalse(vanilla.withPatches(List.of(patch("custom", null))).isModifiable());
        assertNull(vanilla.patches());
        assertNull(vanilla.root());
    }

    /// Existing game patches continue to allow independent modded manifests to be modified.
    @Test
    public void testModifiableWithGamePatch() {
        GameInstanceManifest modded = manifest("example", GameComponentAnalyzer.MOD_LAUNCHER_MAIN, true, null,
                List.of(patch(GameComponentType.GAME.getPatchId(), GameComponentAnalyzer.VANILLA_MAIN)));
        assertTrue(modded.isModifiable());
        assertFalse(modded.withInheritsFrom(new GameInstanceID("parent")).isModifiable());
        assertSame(modded, modded.withBaseGamePatch("1.21.1"));
    }

    /// Base patch conversion leaves unsupported manifests unchanged and is idempotent.
    @Test
    public void testBaseGamePatchConversionBoundaries() {
        GameInstanceManifest vanilla = manifest("example", GameComponentAnalyzer.VANILLA_MAIN, false, null, null);
        GameInstanceManifest converted = vanilla.withBaseGamePatch("1.21.1");
        assertTrue(converted.isRoot());
        assertEquals(1, converted.getPatches().size());
        assertSame(converted, converted.withBaseGamePatch("1.21.2"));
        assertFalse(vanilla.isRoot());
        assertNull(vanilla.patches());

        GameInstanceManifest emptyPatches = vanilla.withPatches(List.of());
        GameInstanceManifest inherited = vanilla.withInheritsFrom(new GameInstanceID("parent"));
        GameInstanceManifest unknown = vanilla.withMainClass("example.Main");
        GameInstanceManifest patched = vanilla.withPatches(List.of(patch("custom", null)));
        assertSame(emptyPatches, emptyPatches.withBaseGamePatch("1.21.1"));
        assertSame(inherited, inherited.withBaseGamePatch("1.21.1"));
        assertSame(unknown, unknown.withBaseGamePatch("1.21.1"));
        assertSame(patched, patched.withBaseGamePatch("1.21.1"));
    }

    /// Installing and removing a loader preserves the original vanilla launch metadata after serialization.
    @Test
    public void testVanillaComponentInstallationRoundTrip() {
        GameInstanceManifest vanilla = GameInstanceManifest.fromJson(JsonParser.parseString("""
                {
                    "id": "renamed-instance",
                    "jar": "1.21.1",
                    "mainClass": "net.minecraft.client.main.Main",
                    "minecraftArguments": "--username Player",
                    "arguments": {"game": ["--version", "1.21.1"], "jvm": ["-Xmx2G"]},
                    "assetIndex": {"id": "17", "url": "https://example.com/assets.json"},
                    "assets": "17",
                    "complianceLevel": 1,
                    "javaVersion": {"component": "java-runtime-delta", "majorVersion": 21},
                    "libraries": [{"name": "example:vanilla:1.0"}],
                    "compatibilityRules": [{"action": "allow"}],
                    "downloads": {"client": {"url": "https://example.com/client.jar"}},
                    "logging": {"client": {
                        "file": {"id": "client.xml", "url": "https://example.com/client.xml"},
                        "argument": "-Dlog.config=${path}", "type": "log4j2-xml"
                    }},
                    "type": "release",
                    "time": "2024-08-08T12:00:00Z",
                    "releaseTime": "2024-08-08T12:00:00Z",
                    "minimumLauncherVersion": 21
                }
                """).getAsJsonObject(), true);
        GameInstancePatch loader = new GameInstancePatch(
                GameComponentType.FABRIC.getPatchId(), "0.16.0", GameInstancePatch.PRIORITY_LOADER,
                new Arguments().addGameArguments("--loader-option"),
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                List.of(new Library("net.fabricmc", "fabric-loader", "0.16.0")));

        GameInstanceManifest installed = vanilla.withBaseGamePatch("1.21.1")
                .addPatch(loader).reconstructByPatches();
        assertEquals(loader.mainClass(), installed.mainClass());
        assertEquals(2, installed.getLibraries().size());
        assertEquals(vanilla.assetIndex(), installed.assetIndex());
        assertEquals(vanilla.downloads(), installed.downloads());
        assertTrue(installed.isModifiable());
        assertEquals("1.21.1", GameComponentAnalyzer.analyze(installed, GameVersionNumber.unknown())
                .getVersion(GameComponentType.GAME));

        GameInstanceManifest restored = GameInstanceManifest.fromJson(installed.toJsonObject(), true)
                .removeComponent(GameComponentType.FABRIC);
        assertEquals(vanilla.toPatch().toJsonObject(), restored.toPatch().toJsonObject());
        assertTrue(restored.isModifiable());
        assertFalse(restored.hasPatch(GameComponentType.FABRIC));
        assertNull(vanilla.patches());
        assertNull(vanilla.root());
    }

    /// Ordinary manifest edits preserve absent nullable Boolean fields.
    @Test
    public void testNullableBooleanFieldsAreNotMaterialized() {
        GameInstanceManifest manifest = manifest("example", null, null, null, null)
                .withJar(new GameInstanceID("parent"));

        JsonObject json = manifest.toJsonObject();
        assertFalse(json.has("root"));
        assertFalse(json.has("hidden"));
    }

    /// Manifest edits update known raw JSON fields without discarding unknown fields.
    @Test
    public void testRawJsonIsPreservedWhenUpdatingKnownFields() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "example");
        json.addProperty("root", false);
        json.addProperty("unknownField", "value");

        GameInstanceManifest manifest = GameInstanceManifest.fromJson(json, true)
                .withRoot(true)
                .withMainClass("example.Main");

        JsonObject updatedJson = manifest.toJsonObject();
        assertEquals("value", updatedJson.get("unknownField").getAsString());
        assertEquals(true, updatedJson.get("root").getAsBoolean());
        assertEquals("example.Main", updatedJson.get("mainClass").getAsString());

        JsonObject rootRemovedJson = manifest.withRoot(null).toJsonObject();
        assertEquals("value", rootRemovedJson.get("unknownField").getAsString());
        assertFalse(rootRemovedJson.has("root"));
    }

    /// Download maps keep the JSON entry order after parsing and rebuilding.
    @Test
    public void testDownloadMapOrderIsPreserved() {
        JsonObject serverDownload = new JsonObject();
        serverDownload.addProperty("url", "server");
        JsonObject clientDownload = new JsonObject();
        clientDownload.addProperty("url", "client");

        JsonObject downloads = new JsonObject();
        downloads.add("SERVER", serverDownload);
        downloads.add("CLIENT", clientDownload);

        JsonObject json = new JsonObject();
        json.addProperty("id", "example");
        json.add("downloads", downloads);

        GameInstanceManifest manifest = GameInstanceManifest.fromJson(json, true);
        assertEquals(List.of(DownloadType.SERVER, DownloadType.CLIENT), List.copyOf(manifest.getDownloads().keySet()));

        JsonObject updatedDownloads = manifest.withDownloads(manifest.getDownloads())
                .toJsonObject()
                .getAsJsonObject("downloads");
        assertEquals(List.of("SERVER", "CLIENT"), List.copyOf(updatedDownloads.keySet()));
    }

    /// Patch edits preserve raw JSON fields and typed inherited instance ids.
    @Test
    public void testPatchParsingAndCopyBehavior() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "patch");
        json.addProperty("mainClass", "old.Main");
        json.addProperty("inheritsFrom", "parent");
        json.addProperty("unknownField", "value");

        GameInstancePatch originalPatch = GameInstancePatch.fromJson(json);
        assertEquals(new GameInstanceID("parent"), originalPatch.inheritsFrom());
        assertSame(originalPatch, originalPatch.withId("patch"));
        assertSame(originalPatch, originalPatch.withMainClass("old.Main"));

        GameInstancePatch patch = originalPatch
                .withMainClass("new.Main")
                .withId((String) null);

        JsonObject updatedJson = patch.toJsonObject();
        assertEquals("value", updatedJson.get("unknownField").getAsString());
        assertEquals("new.Main", updatedJson.get("mainClass").getAsString());
        assertEquals("parent", updatedJson.get("inheritsFrom").getAsString());
        assertFalse(updatedJson.has("id"));

        JsonObject serverDownload = new JsonObject();
        serverDownload.addProperty("url", "server");
        JsonObject clientDownload = new JsonObject();
        clientDownload.addProperty("url", "client");

        JsonObject downloads = new JsonObject();
        downloads.add("SERVER", serverDownload);
        downloads.add("CLIENT", clientDownload);

        JsonObject patchJson = new JsonObject();
        patchJson.addProperty("id", "patch");
        patchJson.add("downloads", downloads);

        GameInstancePatch patchWithDownloads = GameInstancePatch.fromJson(patchJson);
        Map<DownloadType, DownloadInfo> patchDownloads = Objects.requireNonNull(patchWithDownloads.downloads());
        assertEquals(List.of(DownloadType.SERVER, DownloadType.CLIENT), List.copyOf(patchDownloads.keySet()));

        JsonObject updatedDownloads = patchWithDownloads.withDownload(patchDownloads)
                .toJsonObject()
                .getAsJsonObject("downloads");
        assertEquals(List.of("SERVER", "CLIENT"), List.copyOf(updatedDownloads.keySet()));
    }

    /// Creates a minimal manifest for tests.
    private static GameInstanceManifest manifest(
            String id,
            @Nullable String mainClass,
            @Nullable Boolean root,
            @Nullable Boolean hidden,
            @Nullable List<GameInstancePatch> patches) {
        return new GameInstanceManifest(
                new GameInstanceID(id),
                null,
                null,
                mainClass,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                root,
                hidden,
                patches,
                null);
    }

    /// Creates a minimal patch for tests.
    private static GameInstancePatch patch(String id, @Nullable String mainClass) {
        return new GameInstancePatch(
                id,
                null,
                0,
                null,
                null,
                mainClass,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
