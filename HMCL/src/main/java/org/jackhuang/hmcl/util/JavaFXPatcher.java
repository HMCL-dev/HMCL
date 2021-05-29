package org.jackhuang.hmcl.util;

import java.nio.file.Path;
import java.util.Set;

/**
 * Utility for Adding JavaFX to module path.
 *
 * @author ZekerZhayard
 */
public final class JavaFXPatcher {
    private JavaFXPatcher() {
    }

    public static void patch(Set<String> modules, Path... jarPaths) {
        // Nothing to do with Java 8
    }
}
