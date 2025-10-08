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
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.i18n.WenyanUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.time.temporal.TemporalAccessor;

/// @author Glavo
public class Translator_lzh extends Translator {
    public Translator_lzh(SupportedLocale locale) {
        super(locale);
    }

    @Override
    public String getDisplayVersion(RemoteVersion remoteVersion) {
        if (remoteVersion instanceof GameRemoteVersion)
            return WenyanUtils.translateGameVersion(GameVersionNumber.asGameVersion(remoteVersion.getSelfVersion()));
        else
            return WenyanUtils.translateGenericVersion(remoteVersion.getSelfVersion());
    }

    @Override
    public String formatDateTime(TemporalAccessor time) {
        return WenyanUtils.formatDateTime(time);
    }
}
