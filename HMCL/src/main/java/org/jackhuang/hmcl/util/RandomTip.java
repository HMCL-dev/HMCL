/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/ >.
 */
package org.jackhuang.hmcl.util;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RandomTip {

    private static final List<String> tips;
    private static final int maxTipNumber = 30;

    static {
        // Initialization tips list
        tips = IntStream.rangeClosed(1, maxTipNumber)
                .mapToObj(i -> i18n(String.format("message.tips_%s", i)))
                .collect(Collectors.toList());
    }

    public static String getRandomTip() {
        String tip = tips.get(getRandomTipIndex());
        return formatTip(tip);
    }

    private static String formatTip(String tip) {
        StringBuilder formattedTip = new StringBuilder();
        int lineLength = 0;

        for (int i = 0; i < tip.length(); i++) {
            char c = tip.charAt(i);
            int charLength = 1;

            if (Character.toString(c).matches("\\p{IsHan}")) {
                charLength = 2;     // One Chinese character is considered as two characters
            }

            if (lineLength + charLength > 50) {
                // 49 characters per line
                formattedTip.append("\n");
                lineLength = 0;
            }

            formattedTip.append(c);
            lineLength += charLength;
        }

        return formattedTip.toString();
    }

    private static int getRandomTipIndex() {
        return ThreadLocalRandom.current().nextInt(tips.size());
    }
}