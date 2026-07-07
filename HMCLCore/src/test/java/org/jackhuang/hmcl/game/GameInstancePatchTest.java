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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Tests for game instance patch parsing and copy behavior.
@NotNullByDefault
public final class GameInstancePatchTest {

    /// Patch edits update known raw JSON fields without discarding unknown fields.
    @Test
    public void testRawJsonIsPreservedWhenUpdatingKnownFields() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "patch");
        json.addProperty("mainClass", "old.Main");
        json.addProperty("unknownField", "value");

        GameInstancePatch originalPatch = GameInstancePatch.fromJson(json);
        assertSame(originalPatch, originalPatch.withId("patch"));
        assertSame(originalPatch, originalPatch.withMainClass("old.Main"));

        GameInstancePatch patch = originalPatch
                .withMainClass("new.Main")
                .withId(null);

        JsonObject updatedJson = patch.toJsonObject();
        assertEquals("value", updatedJson.get("unknownField").getAsString());
        assertEquals("new.Main", updatedJson.get("mainClass").getAsString());
        assertFalse(updatedJson.has("id"));
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
        json.addProperty("id", "patch");
        json.add("downloads", downloads);

        GameInstancePatch patch = GameInstancePatch.fromJson(json);
        Map<DownloadType, DownloadInfo> patchDownloads = Objects.requireNonNull(patch.downloads());
        assertEquals(List.of(DownloadType.SERVER, DownloadType.CLIENT), List.copyOf(patchDownloads.keySet()));

        JsonObject updatedDownloads = patch.withDownload(patchDownloads)
                .toJsonObject()
                .getAsJsonObject("downloads");
        assertEquals(List.of("SERVER", "CLIENT"), List.copyOf(updatedDownloads.keySet()));
    }

    /// Inherited instance ids are parsed and written through GameInstanceID.
    @Test
    public void testInheritsFromUsesGameInstanceId() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "patch");
        json.addProperty("inheritsFrom", "parent");

        GameInstancePatch patch = GameInstancePatch.fromJson(json);

        assertEquals(new GameInstanceID("parent"), patch.inheritsFrom());
        assertEquals("parent", patch.toJsonObject().get("inheritsFrom").getAsString());
    }
}
