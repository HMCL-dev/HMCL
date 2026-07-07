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

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    /// Root manifests with patch lists resolve from the patch view instead of their own body fields.
    @Test
    public void testRootManifestWithPatchesUsesPatchView() throws NoSuchGameInstanceException {
        GameInstanceManifest manifest = manifest(
                "example",
                "example.Main",
                true,
                false,
                List.of(patch("patch", null)));

        GameInstanceManifest.Resolved resolved = new DefaultGameRepository(Path.of(".")).resolve(manifest);

        assertNull(resolved.launchManifest().mainClass());
        assertNull(resolved.launchManifest().patches());
        assertEquals(List.of(patch("patch", null)), resolved.standaloneManifest().getPatches());
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
                .withId(null);

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
