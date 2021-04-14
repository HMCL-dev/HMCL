package java.lang.module;

import java.nio.file.Path;
import java.util.Set;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public interface ModuleFinder {
    //CHECKSTYLE:OFF
    static ModuleFinder of(Path... entries) {
        throw new UnsupportedOperationException();
    }
    Set<ModuleReference> findAll();
    //CHECKSTYLE:ON
}