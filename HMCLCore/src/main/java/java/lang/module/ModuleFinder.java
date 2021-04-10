package java.lang.module;

import java.util.Set;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public interface ModuleFinder {
    //CHECKSTYLE:OFF
    static ModuleFinder ofSystem() {
        throw new UnsupportedOperationException();
    }
    Set<ModuleReference> findAll();
    //CHECKSTYLE:ON
}
