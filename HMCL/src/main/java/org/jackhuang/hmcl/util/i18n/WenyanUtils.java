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

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;

/**
 * @author Glavo
 */
public final class WenyanUtils {
    private static final String DOT = "點";

    private static final String[] NUMBERS = {
            "〇", "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九",
            "二十", "二十一", "二十二", "二十三", "二十四", "二十五", "二十六", "二十七", "二十八", "二十九",
            "三十", "三十一", "三十二", "三十三", "三十四", "三十五", "三十六", "三十七", "三十八", "三十九",
            "四十", "四十一", "四十二", "四十三", "四十四", "四十五", "四十六", "四十七", "四十八", "四十九",
            "五十", "五十一", "五十二", "五十三", "五十四", "五十五", "五十六", "五十七", "五十八", "五十九",
            "六十", "六十一", "六十二", "六十三", "六十四", "六十五", "六十六", "六十七", "六十八", "六十九",
    };

    private static final char[] SNAPSHOT_SUFFIX = {'甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'};

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

    public static String formatDateTime(TemporalAccessor time) {
        LocalDateTime localDateTime;
        if (time instanceof Instant)
            localDateTime = ((Instant) time).atZone(ZoneId.systemDefault()).toLocalDateTime();
        else
            localDateTime = LocalDateTime.from(time);

        StringBuilder builder = new StringBuilder(16);
        builder.append("西曆");
        appendDigitByDigit(builder, String.valueOf(localDateTime.getYear()));
        builder.append('年');
        builder.append(numberToString(localDateTime.getMonthValue()));
        builder.append('月');
        builder.append(numberToString(localDateTime.getDayOfMonth()));
        builder.append('日');

        builder.append(' ');

        appendDigitByDigit(builder, String.valueOf(localDateTime.getHour()));
        builder.append('时');
        appendDigitByDigit(builder, String.valueOf(localDateTime.getMinute()));
        builder.append('分');
        appendDigitByDigit(builder, String.valueOf(localDateTime.getSecond()));
        builder.append('秒');

        return builder.toString();
    }

    public static String translateGameVersion(GameVersionNumber gameVersion) {
        if (gameVersion instanceof GameVersionNumber.Release) {
            var release = (GameVersionNumber.Release) gameVersion;

            StringBuilder builder = new StringBuilder();
            appendDigitByDigit(builder, String.valueOf(release.getMajor()));
            builder.append(DOT);
            appendDigitByDigit(builder, String.valueOf(release.getMinor()));

            if (release.getPatch() != 0) {
                builder.append(DOT);
                appendDigitByDigit(builder, String.valueOf(release.getPatch()));
            }

            if (release.getEaType() == GameVersionNumber.Release.TYPE_GA) {
                // do nothing
            } else if (release.getEaType() == GameVersionNumber.Release.TYPE_PRE) {
                builder.append("之預");
                appendDigitByDigit(builder, String.valueOf(release.getEaVersion()));
            } else if (release.getEaType() == GameVersionNumber.Release.TYPE_RC) {
                builder.append("之候");
                appendDigitByDigit(builder, String.valueOf(release.getEaVersion()));
            } else {
                // Unsupported
                return gameVersion.toString();
            }

            return builder.toString();
        } else if (gameVersion instanceof GameVersionNumber.Snapshot) {
            var snapshot = (GameVersionNumber.Snapshot) gameVersion;

            StringBuilder builder = new StringBuilder();

            appendDigitByDigit(builder, String.valueOf(snapshot.getYear()));
            builder.append('週');
            appendDigitByDigit(builder, String.valueOf(snapshot.getWeek()));

            char suffix = snapshot.getSuffix();
            if (suffix >= 'a' && (suffix - 'a') < SNAPSHOT_SUFFIX.length)
                builder.append(SNAPSHOT_SUFFIX[suffix - 'a']);
            else
                builder.append(suffix);

            return builder.toString();
        } else if (gameVersion instanceof GameVersionNumber.Special) {

            String version = gameVersion.toString();
            switch (version) {
                case "2.0":
                    return "二點〇";
                case "2.0_blue":
                    return "二點〇藍";
                case "2.0_red":
                    return "二點〇赤";
                case "2.0_purple":
                    return "二點〇紫";
                default:
                    return version;
            }

        } else {
            return gameVersion.toString();
        }
    }

    private WenyanUtils() {
    }
}
