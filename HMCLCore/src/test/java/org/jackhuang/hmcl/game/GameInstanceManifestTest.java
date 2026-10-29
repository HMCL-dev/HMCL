// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
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
