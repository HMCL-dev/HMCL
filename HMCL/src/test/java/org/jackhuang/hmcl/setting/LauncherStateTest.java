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
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached launcher state migration.
@NotNullByDefault
public final class LauncherStateTest {
    /// Tests that settings listeners receive the observable field that changed.
    @Test
    public void reportsChangedFieldToListeners() {
        LauncherState state = new LauncherState();
        AtomicBoolean notified = new AtomicBoolean();
        state.addListener(source -> {
            assertSame(state.xProperty(), source);
            notified.set(true);
        });

        state.setX(0.25);

        assertTrue(notified.get());
    }

    /// Tests that only window geometry fields defer automatic persistence.
    @Test
    public void identifiesDeferredWindowGeometryFields() {
        LauncherState state = new LauncherState();

        assertFalse(state.shouldSaveImmediately(state.xProperty()));
        assertFalse(state.shouldSaveImmediately(state.yProperty()));
        assertFalse(state.shouldSaveImmediately(state.widthProperty()));
        assertFalse(state.shouldSaveImmediately(state.heightProperty()));
        assertTrue(state.shouldSaveImmediately(state.schemaProperty()));
        assertTrue(state.shouldSaveImmediately(state.promptedVersionProperty()));
        assertTrue(state.shouldSaveImmediately(state.getShownTips()));
    }

    /// Tests extracting runtime state fields from a legacy config object.
    @Test
    public void extractsLauncherStateFromLegacyConfigJson() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "x": 0.25,
                  "y": 0.5,
                  "width": 1280.0,
                  "height": 720.0,
                  "promptedVersion": "3.6.15",
                  "shownTips": {
                    "javaVersionTip": 21
                  },
                  "logLines": 5000,
                  "localization": "en"
                }
                """).getAsJsonObject();

        LauncherState state = LegacyConfigMigrator.extractLauncherState(settings);

        assertFalse(settings.has("x"));
        assertFalse(settings.has("y"));
        assertFalse(settings.has("width"));
        assertFalse(settings.has("height"));
        assertFalse(settings.has("promptedVersion"));
        assertFalse(settings.has("shownTips"));
        assertTrue(settings.has("logLines"));
        assertTrue(settings.has("localization"));

        assertEquals(0.25, state.getX(), 1e-9);
        assertEquals(0.5, state.getY(), 1e-9);
        assertEquals(1264.0, state.getWidth());
        assertEquals(704.0, state.getHeight());
        assertEquals("3.6.15", state.getPromptedVersion());
        assertEquals(21.0, state.getShownTips().get("javaVersionTip"));
        assertEquals(LauncherState.CURRENT_SCHEMA, state.getSchema());

        LauncherSettings config = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        assertEquals(5000, config.logLinesProperty().get());
    }
}
