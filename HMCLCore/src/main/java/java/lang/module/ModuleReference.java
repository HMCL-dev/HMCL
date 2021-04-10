package java.lang.module;

import java.io.IOException;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public abstract class ModuleReference {
    //CHECKSTYLE:OFF
    public abstract ModuleReader open() throws IOException;
    //CHECKSTYLE:ON
}
