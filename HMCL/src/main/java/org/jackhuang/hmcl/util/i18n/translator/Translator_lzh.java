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
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public class Translator_lzh extends Translator {
    private static final String DOT = "點";

    private static final String[] NUMBERS = {
            "〇", "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九",
            "廿", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九",
            "卅", "卅一", "卅二", "卅三", "卅四", "卅五", "卅六", "卅七", "卅八", "卅九",
            "卌", "卌一", "卌二", "卌三", "卌四", "卌五", "卌六", "卌七", "卌八", "卌九",
            "五十", "五十一", "五十二", "五十三", "五十四", "五十五", "五十六", "五十七", "五十八", "五十九",
            "六十", "六十一", "六十二", "六十三", "六十四", "六十五", "六十六", "六十七", "六十八", "六十九",
    };

    private static final char[] TIAN_GAN = {'甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'};
    private static final char[] DI_ZHI = {'子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'};

    private static String digitToString(char digit) {
        return digit >= '0' && digit <= '9'
                ? NUMBERS[digit - '0']
                : String.valueOf(digit);
    }

    private static String numberToString(int number) {
        return number >= 0 && number < NUMBERS.length ? NUMBERS[number] : String.valueOf(number);
    }

    private static void appendDigitByDigit(StringBuilder builder, String number) {
        for (int i = 0; i < number.length(); i++) {
            builder.append(digitToString(number.charAt(i)));
        }
    }

    private static int mod(int a, int b) {
        int r = a % b;
        return r >= 0 ? r : r + b;
    }

    static void appendYear(StringBuilder builder, int year) {
        int yearOffset = year - 1984;

        builder.append(TIAN_GAN[mod(yearOffset, TIAN_GAN.length)]);
        builder.append(DI_ZHI[mod(yearOffset, DI_ZHI.length)]);
    }

    static void appendHour(StringBuilder builder, int hour) {
        builder.append(DI_ZHI[((hour + 1) % 24) / 2]);
        builder.append(hour % 2 == 0 ? '正' : '初');
    }

    public static String translateGameVersion(GameVersionNumber gameVersion) {
        if (gameVersion instanceof GameVersionNumber.Release release) {
            StringBuilder builder = new StringBuilder();
            appendDigitByDigit(builder, String.valueOf(release.getMajor()));
            builder.append(DOT);
            appendDigitByDigit(builder, String.valueOf(release.getMinor()));

            if (release.getPatch() != 0) {
                builder.append(DOT);
                appendDigitByDigit(builder, String.valueOf(release.getPatch()));
            }

            switch (release.getEaType()) {
                case GA -> {
                    // do nothing
                }
                case PRE_RELEASE -> {
                    builder.append("之預");
                    appendDigitByDigit(builder, release.getEaVersion().toString());
                }
                case RELEASE_CANDIDATE -> {
                    builder.append("之候");
                    appendDigitByDigit(builder, release.getEaVersion().toString());
                }
                default -> {
                    // Unsupported
                    return gameVersion.toString();
                }
            }

            switch (release.getAdditional()) {
                case NONE -> {
                }
                case UNOBFUSCATED -> {
                    builder.append("涇渭");
                }
                default -> {
                    // Unsupported
                    return gameVersion.toString();
                }
            }

            return builder.toString();
        } else if (gameVersion instanceof GameVersionNumber.LegacySnapshot snapshot) {
            StringBuilder builder = new StringBuilder();

            appendDigitByDigit(builder, String.valueOf(snapshot.getYear()));
            builder.append('週');
            appendDigitByDigit(builder, String.valueOf(snapshot.getWeek()));

            char suffix = snapshot.getSuffix();
            if (suffix >= 'a' && (suffix - 'a') < TIAN_GAN.length)
                builder.append(TIAN_GAN[suffix - 'a']);
            else
                builder.append(suffix);

            if (snapshot.isUnobfuscated())
                builder.append("涇渭");

            return builder.toString();
        } else if (gameVersion instanceof GameVersionNumber.Special) {
            String version = gameVersion.toString();
            return switch (version.toLowerCase(Locale.ROOT)) {
                case "2.0" -> "二點〇";
                case "2.0_blue" -> "二點〇藍";
                case "2.0_red" -> "二點〇赤";
                case "2.0_purple" -> "二點〇紫";
                case "1.rv-pre1" -> "一點真視之預一";
                case "3d shareware v1.34" -> "躍然享件一點三四";
                case "13w12~" -> "一三週一二閏";
                case "20w14infinite", "20w14~", "20w14∞" -> "二〇週一四宇";
                case "22w13oneblockatatime" -> "二二週一三典";
                case "23w13a_or_b" -> "二三週一三暨";
                case "24w14potato" -> "二四週一四芋";
                case "25w14craftmine" -> "二五週一四礦";
                default -> version;
            };
        } else {
            return gameVersion.toString();
        }
    }

    private static final Pattern GENERIC_VERSION_PATTERN =
            Pattern.compile("^[0-9]+(\\.[0-9]+)*");

    public static String translateGenericVersion(String version) {
        Matcher matcher = GENERIC_VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            String prefix = matcher.group();
            StringBuilder builder = new StringBuilder(version.length());

            for (int i = 0; i < prefix.length(); i++) {
                char ch = prefix.charAt(i);
                if (ch >= '0' && ch <= '9')
                    builder.append(digitToString(ch));
                else if (ch == '.')
                    builder.append(DOT);
                else
                    builder.append(ch);
            }
            builder.append(version, prefix.length(), version.length());
            return builder.toString();
        }

        return version;
    }

    public Translator_lzh(SupportedLocale locale) {
        super(locale);
    }

    @Override
    public String getDisplayVersion(RemoteVersion remoteVersion) {
        if (remoteVersion instanceof GameRemoteVersion)
            return translateGameVersion(GameVersionNumber.asGameVersion(remoteVersion.getSelfVersion()));
        else
            return translateGenericVersion(remoteVersion.getSelfVersion());
    }

    @Override
    public String getDisplayVersion(GameVersionNumber versionNumber) {
        return translateGameVersion(versionNumber);
    }

    @Override
    public String formatDateTime(TemporalAccessor time) {
        LocalDateTime localDateTime;
        if (time instanceof Instant instant)
            localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        else
            localDateTime = LocalDateTime.from(time);

        StringBuilder builder = new StringBuilder(16);

        appendYear(builder, localDateTime.getYear());
        builder.append('年');
        builder.append(numberToString(localDateTime.getMonthValue()));
        builder.append('月');
        builder.append(numberToString(localDateTime.getDayOfMonth()));
        builder.append('日');

        builder.append(' ');

        appendHour(builder, localDateTime.getHour());
        builder.append(numberToString(localDateTime.getMinute()));
        builder.append('分');
        builder.append(numberToString(localDateTime.getSecond()));
        builder.append('秒');

        return builder.toString();
    }
}
