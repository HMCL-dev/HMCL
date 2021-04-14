package jdk.internal.loader;

import java.lang.module.ModuleReference;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public class BuiltinClassLoader extends ClassLoader {
    public void loadModule(ModuleReference mref) {
        throw new UnsupportedOperationException();
    }
}
