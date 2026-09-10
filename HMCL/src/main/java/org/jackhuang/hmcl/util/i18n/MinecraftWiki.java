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
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.translator.Translator_lzh;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MinecraftWiki {

    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("^[0-9]{2}w[0-9]{2}.+$");

    public static String getWikiLink(SupportedLocale locale, GameRemoteVersion version) {
        String wikiVersion = StringUtils.removeSuffix(version.getSelfVersion(), "_unobfuscated");
        var gameVersion = GameVersionNumber.asGameVersion(wikiVersion);

        if (locale.getLocale().getLanguage().equals("lzh")) {
            String translatedVersion;
            if (wikiVersion.startsWith("2.0"))
                translatedVersion = "二點〇";
            else if (wikiVersion.startsWith("1.0.0-rc2"))
                translatedVersion = Translator_lzh.translateGameVersion(GameVersionNumber.asGameVersion("1.0.0-rc2"));
            else
                translatedVersion = Translator_lzh.translateGameVersion(gameVersion);

            if (translatedVersion.equals(gameVersion.toString()) || gameVersion instanceof GameVersionNumber.Old) {
                return getWikiLink(SupportedLocale.getLocale(LocaleUtils.LOCALE_ZH_HANT), version);
            } else if (SNAPSHOT_PATTERN.matcher(wikiVersion).matches()) {
                return locale.i18n("wiki.version.game.snapshot", translatedVersion);
            } else {
                return locale.i18n("wiki.version.game", translatedVersion);
            }
        }

        String variantSuffix;
        if (LocaleUtils.isChinese(locale.getLocale())) {
            if (!"Hant".equals(LocaleUtils.getScript(locale.getLocale()))) {
                variantSuffix = "?variant=zh-cn";
            } else {
                String region = locale.getLocale().getCountry();
                variantSuffix = region.equals("HK") || region.equals("MO")
                        ? "?variant=zh-hk"
                        : "?variant=zh-tw";
            }
        } else
            variantSuffix = "";

        replace:
        if (gameVersion instanceof GameVersionNumber.Release) {
            if (wikiVersion.startsWith("1.0")) {
                if (wikiVersion.equals("1.0"))
                    wikiVersion = "1.0.0";
                else if (wikiVersion.startsWith("1.0.0-rc2"))
                    wikiVersion = "1.0.0-rc2";
            }
        } else if (gameVersion instanceof GameVersionNumber.LegacySnapshot) {
            return locale.i18n("wiki.version.game.snapshot", wikiVersion) + variantSuffix;
        } else {
            if (wikiVersion.length() >= 6 && wikiVersion.charAt(2) == 'w') {
                // Starting from 2020, all April Fools' versions follow this pattern
                if (SNAPSHOT_PATTERN.matcher(wikiVersion).matches()) {
                    if (wikiVersion.equals("22w13oneblockatatime"))
                        wikiVersion = "22w13oneBlockAtATime";
                    return locale.i18n("wiki.version.game.snapshot", wikiVersion) + variantSuffix;
                }
            }

            String lower = wikiVersion.toLowerCase(Locale.ROOT);
            switch (lower) {
                case "0.30-1":
                case "0.30-2":
                case "c0.30_01c":
                    wikiVersion = "Classic_0.30";
                    break replace;
                case "in-20100206-2103":
                    wikiVersion = "Indev_20100206";
                    break replace;
                case "inf-20100630-1":
                    wikiVersion = "Infdev_20100630";
                    break replace;
                case "inf-20100630-2":
                    wikiVersion = "Alpha_v1.0.0";
                    break replace;
                case "1.19_deep_dark_experimental_snapshot-1":
                    wikiVersion = "1.19-exp1";
                    break replace;
                case "in-20100130":
                    wikiVersion = "Indev_0.31_20100130";
                    break replace;
                case "b1.6-tb3":
                    wikiVersion = "Beta_1.6_Test_Build_3";
                    break replace;
                case "1.14_combat-212796":
                    wikiVersion = "1.14.3_-_Combat_Test";
                    break replace;
                case "1.14_combat-0":
                    wikiVersion = "Combat_Test_2";
                    break replace;
                case "1.14_combat-3":
                    wikiVersion = "Combat_Test_3";
                    break replace;
                case "1_15_combat-1":
                    wikiVersion = "Combat_Test_4";
                    break replace;
                case "1_15_combat-6":
                    wikiVersion = "Combat_Test_5";
                    break replace;
                case "1_16_combat-0":
                    wikiVersion = "Combat_Test_6";
                    break replace;
                case "1_16_combat-1":
                    wikiVersion = "Combat_Test_7";
                    break replace;
                case "1_16_combat-2":
                    wikiVersion = "Combat_Test_7b";
                    break replace;
                case "1_16_combat-3":
                    wikiVersion = "Combat_Test_7c";
                    break replace;
                case "1_16_combat-4":
                    wikiVersion = "Combat_Test_8";
                    break replace;
                case "1_16_combat-5":
                    wikiVersion = "Combat_Test_8b";
                    break replace;
                case "1_16_combat-6":
                    wikiVersion = "Combat_Test_8c";
                    break replace;
            }

            if (lower.startsWith("2.0"))
                wikiVersion = "2.0";
            else if (lower.startsWith("b1.8-pre1"))
                wikiVersion = "Beta_1.8-pre1";
            else if (lower.startsWith("b1.1-"))
                wikiVersion = "Beta_1.1";
            else if (lower.startsWith("a1.1.0"))
                wikiVersion = "Alpha_v1.1.0";
            else if (lower.startsWith("a1.0.14"))
                wikiVersion = "Alpha_v1.0.14";
            else if (lower.startsWith("a1.0.13_01"))
                wikiVersion = "Alpha_v1.0.13_01";
            else if (lower.startsWith("in-20100214"))
                wikiVersion = "Indev_20100214";
            else if (lower.contains("experimental-snapshot"))
                wikiVersion = lower.replace("_experimental-snapshot-", "-exp");
            else if (lower.startsWith("inf-"))
                wikiVersion = lower.replace("inf-", "Infdev_");
            else if (lower.startsWith("in-"))
                wikiVersion = lower.replace("in-", "Indev_");
            else if (lower.startsWith("rd-"))
                wikiVersion = "pre-Classic_" + lower;
            else if (lower.startsWith("b"))
                wikiVersion = lower.replace("b", "Beta_");
            else if (lower.startsWith("a"))
                wikiVersion = lower.replace("a", "Alpha_v");
            else if (lower.startsWith("c"))
                wikiVersion = lower
                        .replace("c", "Classic_")
                        .replace("st", "SURVIVAL_TEST");
        }

        return locale.i18n("wiki.version.game", wikiVersion) + variantSuffix;
    }

    private MinecraftWiki() {
    }
}
