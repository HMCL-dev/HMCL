// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacDeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacModel;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/// Formats device identities for the UI without changing their diagnostic names.
@NotNullByDefault
public final class DeviceInfoFormatter {

    /// Prevents instantiation of this utility class.
    private DeviceInfoFormatter() {
    }

    /// Localizes known Mac model details using the supplied bundle; other identities retain their names.
    public static String format(DeviceInfo device, ResourceBundle bundle) {
        if (!(device instanceof MacDeviceInfo mac))
            return device.name();
        @Nullable MacModel model = mac.model();
        if (model == null)
            return device.name();

        List<String> details = new ArrayList<>();
        for (MacModel.Detail detail : model.details()) {
            details.add(bundle.getString("about.device.mac." + detail.type()).formatted(detail.arguments().toArray()));
        }
        return details.isEmpty() ? model.productName()
                : bundle.getString("about.device.mac.name").formatted(model.productName(),
                String.join(bundle.getString("about.device.mac.separator"), details));
    }
}
