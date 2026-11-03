// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacDeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacModel;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies bundled Mac data and localized presentation without a running JavaFX application.
@NotNullByDefault
public final class DeviceInfoFormatterTest {

    /// Formats a device with the requested bundled locale.
    private static String format(DeviceInfo device, String languageTag) {
        return DeviceInfoFormatter.format(device,
                ResourceBundle.getBundle("assets.lang.I18N", Locale.forLanguageTag(languageTag),
                        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT)));
    }

    /// Localizes details while keeping product names and diagnostic names stable.
    @Test
    public void testLocalizedModels() {
        var mini = new MacDeviceInfo("Mac mini", "Macmini9,1");
        assertEquals("Mac mini (M1, 2020)", mini.name());
        assertEquals("Mac mini (M1, 2020)", format(mini, "en"));
        assertEquals("Mac mini (M1，2020 年)", format(mini, "zh-Hans"));
        assertEquals("Mac mini (M1，2020 年)", format(mini, "zh-Hant"));
        var pro = new MacDeviceInfo("MacBook Pro", "MacBookPro18,3");
        assertEquals("MacBook Pro (14 英寸，2021 年)", format(pro, "zh-Hans"));
        assertEquals("MacBook Pro (14 吋，2021 年)", format(pro, "zh-Hant"));
        var air = new MacDeviceInfo("MacBook Air", "MacBookAir6,2");
        assertEquals("MacBook Air (13 英寸，2013 年中或 2014 年初)", format(air, "zh-Hans"));
        assertEquals("Mac mini (M1, 2020)", mini.name());
    }

    /// Unknown models and simple identities preserve their supplied names.
    @Test
    public void testFallbackNames() {
        assertEquals("Future Mac", format(new MacDeviceInfo("Future Mac", "Mac999,1"), "zh-Hans"));
        assertEquals("Mac999,1", format(new MacDeviceInfo("Mac999,1", "Mac999,1"), "zh-Hant"));
        assertEquals("Mac mini", format(new MacDeviceInfo("Mac mini", null), "zh-Hans"));
        assertEquals("Raspberry Pi 5", format(new DeviceInfo.Simple("Raspberry Pi 5"), "zh-Hans"));
        assertEquals("Mac mini (M1, 2020)", new MacDeviceInfo("Macmini9,1", "Macmini9,1").name());
    }

    /// Every shipped mapping can be loaded and formatted in all three supported description languages.
    @Test
    public void testAllBundledModels() throws IOException {
        try (@Nullable InputStream input = MacModel.class.getResourceAsStream("/assets/platform/mac-models.csv")) {
            assertNotNull(input);
            try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8))) {
                int modelCount = 0;
                @Nullable String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank())
                        continue;
                    String @Unmodifiable [] columns = line.split(",", -1);
                    String identifier = columns[0] + "," + columns[1];
                    assertNotNull(MacModel.find(identifier), identifier);
                    var device = new MacDeviceInfo(identifier, identifier);
                    assertEquals(device.name(), format(device, "en"), identifier);
                    for (String locale : List.of("en", "zh-Hans", "zh-Hant")) {
                        String formatted = format(device, locale);
                        assertFalse(formatted.isBlank(), identifier);
                        assertFalse(formatted.contains("%s"), identifier);
                        assertFalse(formatted.contains("null"), identifier);
                    }
                    modelCount++;
                }
                assertTrue(modelCount > 0);
            }
        }
    }
}
