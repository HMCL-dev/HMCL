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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import java.util.*;

import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class RandomTip {

    private static final List<String> tips;
    private static final int maxTipNumber = 30;

    private static final GuaranteedRandomIndex indexGenerator;

    static {
        // Initialization tips list
        tips = IntStream.rangeClosed(1, maxTipNumber)
                .mapToObj(i -> i18n(String.format("message.tips_%s", i)))
                .collect(Collectors.toList());

        indexGenerator = new GuaranteedRandomIndex(tips.size());
    }

    public static String getRandomTip() {
        String tip = tips.get(indexGenerator.next());
        return formatTip(tip);
    }

    public static String getRandomTip(String previous) {
        return getRandomTip();
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

            // avoid leaving a single punctuation on the new line
            if (lineLength + charLength > 51 &&
                    !(Character.toString(c).matches("\\p{P}") && lineLength + charLength == 52)) {
                formattedTip.append('\n');
                lineLength = 0;
            }

            formattedTip.append(c);
            lineLength += charLength;
        }

        return formattedTip.toString();
    }

    private static final class GuaranteedRandomIndex {
        private final List<Integer> indices;
        private final Random random;
        private int cursor;

        GuaranteedRandomIndex(int size) {
            this.indices = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                indices.add(i);
            }
            this.random = new Random();
            shuffle();
            this.cursor = 0;
        }

        private void shuffle() {
            for (int i = indices.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Collections.swap(indices, i, j);
            }
        }

        int next() {
            if (cursor >= indices.size()) {
                shuffle();
                cursor = 0;
            }
            return indices.get(cursor++);
        }
    }
    private RandomTip() {}
}