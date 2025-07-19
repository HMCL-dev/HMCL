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
package org.jackhuang.hmcl.util.logging;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author Glavo
 */
final class CallerFinder {
    private static final String PACKAGE_PREFIX = CallerFinder.class.getPackageName() + ".";
    private static final Predicate<StackWalker.StackFrame> PREDICATE = stackFrame -> !stackFrame.getClassName().startsWith(PACKAGE_PREFIX);
    private static final Function<Stream<StackWalker.StackFrame>, Optional<StackWalker.StackFrame>> FUNCTION = stream -> stream.filter(PREDICATE).findFirst();
    private static final Function<StackWalker.StackFrame, String> FRAME_MAPPING = frame -> frame.getClassName() + "." + frame.getMethodName();

    static String getCaller() {
        return StackWalker.getInstance().walk(FUNCTION).map(FRAME_MAPPING).orElse(null);
    }

    private CallerFinder() {
    }
}
