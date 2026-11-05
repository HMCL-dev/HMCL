// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n.translator;

import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.platform.hardware.DeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacDeviceInfo;
import org.jackhuang.hmcl.util.platform.macos.MacModel;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// @author Glavo
public class Translator {
    protected final SupportedLocale supportedLocale;
    protected final Locale displayLocale;

    public Translator(SupportedLocale supportedLocale) {
        this.supportedLocale = supportedLocale;
        this.displayLocale = supportedLocale.getDisplayLocale();
    }

    public final SupportedLocale getSupportedLocale() {
        return supportedLocale;
    }

    public final Locale getDisplayLocale() {
        return displayLocale;
    }

    public String getDisplayVersion(ComponentRemoteVersion remoteVersion) {
        return remoteVersion.getSelfVersion();
    }

    public String getDisplayVersion(GameVersionNumber versionNumber) {
        return versionNumber.toNormalizedString();
    }

    /// Localizes known Mac model sizes and release dates; other identities retain their names.
    /// Chip names from the model table are preserved verbatim.
    /// Diagnostic names and cached device information are not modified.
    public @NotNull String formatDeviceName(@NotNull DeviceInfo device) {
        if (!(device instanceof MacDeviceInfo mac))
            return device.name();
        @Nullable MacModel model = MacModel.find(mac.modelIdentifier());
        if (model == null)
            return device.name();

        List<String> details = new ArrayList<>();
        for (MacModel.Detail detail : model.details()) {
            if (detail.type().equals("chip")) {
                details.add(detail.arguments().get(0));
            } else if (detail.type().equals("month")) {
                YearMonth month = YearMonth.of(Integer.parseInt(detail.arguments().get(0)), Integer.parseInt(detail.arguments().get(1)));
                details.add(month.format(DateTimeFormatter.ofPattern(supportedLocale.i18n("about.device.mac.month"), displayLocale)));
            } else {
                details.add(supportedLocale.i18n("about.device.mac." + detail.type(), detail.arguments().toArray()));
            }
        }
        return details.isEmpty() ? model.productName()
                : supportedLocale.i18n("about.device.mac.name", model.productName(),
                String.join(supportedLocale.i18n("about.device.mac.separator"), details));
    }

    /// @see [#formatDateTime(TemporalAccessor)]
    protected DateTimeFormatter dateTimeFormatter;

    public String formatDateTime(TemporalAccessor time) {
        DateTimeFormatter formatter = dateTimeFormatter;
        if (formatter == null) {
            formatter = dateTimeFormatter = DateTimeFormatter.ofPattern(supportedLocale.getResourceBundle().getString("datetime.format"))
                    .withZone(ZoneId.systemDefault());
        }
        return formatter.format(time);
    }

    public String formatSpeed(long bytes) {
        if (bytes < 1024) {
            return supportedLocale.i18n("download.speed.byte_per_second", bytes);
        } else if (bytes < 1024 * 1024) {
            return supportedLocale.i18n("download.speed.kibibyte_per_second", (double) bytes / 1024);
        } else {
            return supportedLocale.i18n("download.speed.megabyte_per_second", (double) bytes / (1024 * 1024));
        }
    }
}
