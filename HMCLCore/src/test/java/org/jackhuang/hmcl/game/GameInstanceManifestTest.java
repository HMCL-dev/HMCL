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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
