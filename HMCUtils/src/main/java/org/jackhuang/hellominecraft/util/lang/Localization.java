/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util.lang;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
public final class Localization {

    private static final String ROOT_LOCATION = "/org/jackhuang/hellominecraft/lang/I18N%s.lang";

    private static final Map<Locale, Localization> INSTANCE = new HashMap<>();

    private final Map<String, String> lang = new HashMap<>();
    
    private static InputStream getStream(String id) {
        return Localization.class.getResourceAsStream(String.format(ROOT_LOCATION, id));
    }

    private Localization(Locale locale) {
        InputStream is = getStream("_" + locale.getLanguage() + "_" + locale.getCountry());
        if (is == null)
            is = getStream("_" + locale.getLanguage());
        if (is == null)
            is = getStream("");
        if (is == null)
            throw new InternalError("Language file missing");

        try {
            for (String s : IOUtils.readLines(is, IOUtils.DEFAULT_CHARSET))
                if (!s.isEmpty() && s.charAt(0) != 35) {
                    int i = s.indexOf('=');
                    if (i == -1)
                        continue;
                    lang.put(s.substring(0, i), s.substring(i + 1));
                }
        } catch (IOException ex) {
           throw new InternalError("Language file missing", ex);
        }
    }

    public synchronized String localize(String key) {
        String s = lang.get(key);
        return s == null ? key : s;
    }

    public static Localization get(Locale l) {
        if (!INSTANCE.containsKey(l))
            INSTANCE.put(l, new Localization(l));
        return INSTANCE.get(l);
    }

}
