package org.jackhuang.hmcl.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import jdk.internal.loader.BuiltinClassLoader;

/**
 * Utility for Adding JavaFX to module path.
 *
 * @author ZekerZhayard
 */
public class JavaFXPatcher {
    private static final String[] addOpens = {
            "javafx.base/com.sun.javafx.runtime",
            "javafx.base/com.sun.javafx.binding",
            "javafx.base/com.sun.javafx.event",
            "javafx.graphics/javafx.css",
            "javafx.graphics/com.sun.javafx.stage",
            "javafx.controls/com.sun.javafx.scene.control.behavior",
            "javafx.controls/javafx.scene.control.skin",
            "javafx.controls/com.sun.javafx.scene.control"
    };

    private static final String[] addExports = {
            "javafx.base/com.sun.javafx.binding",
            "javafx.base/com.sun.javafx.event",
            "javafx.graphics/com.sun.javafx.stage",
            "javafx.controls/com.sun.javafx.scene.control.behavior",
            "javafx.controls/javafx.scene.control.skin",
            "javafx.controls/com.sun.javafx.scene.control"
    };

    /**
     * Add JavaFX to module path at runtime.
     *
     * @param modules  All module names
     * @param jarPaths All jar paths
     * @throws ReflectiveOperationException When the call to add these jars to the system module path failed.
     */
    public static void patch(Set<String> modules, Path... jarPaths) throws ReflectiveOperationException {
        // Find all modules
        ModuleFinder finder = ModuleFinder.of(jarPaths);

        // Load all modules as unnamed module
        for (ModuleReference mref : finder.findAll()) {
            ((BuiltinClassLoader) ClassLoader.getSystemClassLoader()).loadModule(mref);
        }

        // Define all modules
        Configuration config = Configuration.resolveAndBind(finder, List.of(ModuleLayer.boot().configuration()), finder, modules);
        ModuleLayer layer = ModuleLayer.defineModules(config, List.of(ModuleLayer.boot()), name -> ClassLoader.getSystemClassLoader()).layer();

        // Add-Exports and Add-Opens
        try {
            // Some hacks
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Module.class, MethodHandles.lookup());
            addExportsOrOpens(addExports, layer, lookup.findVirtual(Module.class, "implAddExportsToAllUnnamed", MethodType.methodType(void.class, String.class)));
            addExportsOrOpens(addOpens, layer, lookup.findVirtual(Module.class, "implAddOpensToAllUnnamed", MethodType.methodType(void.class, String.class)));
        } catch (Throwable t) {
            throw new ReflectiveOperationException(t);
        }
    }

    private static void addExportsOrOpens(String[] targets, ModuleLayer layer, MethodHandle handle) {
        for (String target : targets) {
            String[] name = target.split("/", 2); // <module>/<package>
            layer.findModule(name[0]).ifPresent(m -> {
                try {
                    handle.invokeWithArguments(m, name[1]);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            });
        }
    }
}
