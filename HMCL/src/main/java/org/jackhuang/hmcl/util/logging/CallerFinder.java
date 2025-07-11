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
