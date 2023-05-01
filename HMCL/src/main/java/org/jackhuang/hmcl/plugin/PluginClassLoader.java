package org.jackhuang.hmcl.plugin;

import org.jackhuang.hmcl.plugin.api.PluginAccessible;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.plugin.api.PluginUnsafeInterface;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;

public final class PluginClassLoader extends URLClassLoader {
    private static final Class<PluginAccessible> pluginAccessibleClass = PluginAccessible.class;

    private static final Class<PluginUnsafeInterface> pluginUnsafeInterfaceClass = PluginUnsafeInterface.class;

    private final File pluginFile;

    private PluginClassLoader(File pluginFile) throws MalformedURLException {
        super(new URL[]{pluginFile.getAbsoluteFile().toURI().toURL()});
        this.pluginFile = pluginFile.getAbsoluteFile();
    }

    public static PluginClassLoader of(File pluginFile) {
        try {
            return new PluginClassLoader(pluginFile);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public File getPluginFile() {
        return this.pluginFile;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.equals(pluginUnsafeInterfaceClass.getName())) {
            return this.getClass().getClassLoader().loadClass(name);
        } else if (name.startsWith("org.jackhuang.hmcl.") || name.startsWith("java.") || name.startsWith("jdk.")) {
            Class<?> loadedClazz = PluginUnsafeInterface.runUnsafe(() -> {
                try {
                    Class<?> clazz = this.getClass().getClassLoader().loadClass(name);
                    if (clazz.getName().startsWith("org.jackhuang.hmcl") && clazz.getAnnotation(pluginAccessibleClass) == null) {
                        PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, String.format("Suppress Class \"%s\" to be loaded.", clazz.getName()));
                        throw new ClassNotFoundException();
                    }
                    return clazz;
                } catch (ClassNotFoundException ignored) {
                }
                return null;
            });

            if (loadedClazz == null) {
                throw new ClassNotFoundException(name);
            } else {
                return loadedClazz;
            }
        } else {
            return this.findClass(name);
        }
    }
}
