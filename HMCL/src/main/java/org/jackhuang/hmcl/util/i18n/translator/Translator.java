/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.i18n.translator;

import org.jackhuang.hmcl.download.RemoteVersion;
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

    public String getDisplayVersion(RemoteVersion remoteVersion) {
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
