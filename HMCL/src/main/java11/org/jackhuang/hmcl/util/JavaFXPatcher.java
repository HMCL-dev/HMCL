package org.jackhuang.hmcl.util;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;

import jdk.internal.loader.BuiltinClassLoader;

public class JavaFXPatcher {
    public static void patch(Path... jarPaths) {
        for (ModuleReference mref : ModuleFinder.of(jarPaths).findAll()) {
            ((BuiltinClassLoader) ClassLoader.getSystemClassLoader()).loadModule(mref);
        }
    }
}
