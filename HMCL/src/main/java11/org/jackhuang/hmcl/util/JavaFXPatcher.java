package org.jackhuang.hmcl.util;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.internal.loader.BuiltinClassLoader;

/**
 * Utility for Adding JavaFX to module path.
 *
 * @author ZekerZhayard
 */
public class JavaFXPatcher {
    /**
     * Add JavaFX to module path at runtime.
     *
     * @param modules All module names
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
        try (
            FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + JavaFXPatcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()), Map.of("create", "true"));
            InputStream stream = Files.newInputStream(fs.getPath("/META-INF/MANIFEST.MF"))
        ) {
            // Some hacks
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Module.class, MethodHandles.lookup());
            Attributes attributes = new Manifest(stream).getMainAttributes();
            addExportsOrOpens(attributes.getValue("Add-Exports"), layer, lookup.findVirtual(Module.class, "implAddExportsToAllUnnamed", MethodType.methodType(void.class, String.class)));
            addExportsOrOpens(attributes.getValue("Add-Opens"), layer, lookup.findVirtual(Module.class, "implAddOpensToAllUnnamed", MethodType.methodType(void.class, String.class)));
        } catch (Throwable t) {
            throw new ReflectiveOperationException(t);
        }
    }

    private static void addExportsOrOpens(String targets, ModuleLayer layer, MethodHandle handle) {
        for (String target : targets.split("\\s+")) {
            String[] name = target.split("/", 2); // <module>/<package>
            if (name[0].startsWith("javafx.")) {
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
}
