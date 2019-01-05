/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class ExtractRules {

    public static final ExtractRules EMPTY = new ExtractRules();

    private final List<String> exclude;

    public ExtractRules() {
        this.exclude = Collections.emptyList();
    }

    public ExtractRules(List<String> exclude) {
        this.exclude = new LinkedList<>(exclude);
    }

    public List<String> getExclude() {
        return Collections.unmodifiableList(exclude);
    }

    public boolean shouldExtract(String path) {
        return exclude.stream().noneMatch(path::startsWith);
    }

}
