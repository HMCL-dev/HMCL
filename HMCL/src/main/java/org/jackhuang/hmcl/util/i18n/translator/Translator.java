// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n.translator;

import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
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
