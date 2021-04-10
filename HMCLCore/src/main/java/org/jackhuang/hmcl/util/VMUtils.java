package org.jackhuang.hmcl.util;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;

/**
 * Dependent and non-dependent platform utilities for VM.
 *
 * @author xxDark
 */
public final class VMUtils {
    private static int vmVersion = -1;

    /**
     * Deny all constructions.
     */
    private VMUtils() { }

    /**
     * Appends URL to the {@link URLClassLoader}.
     *
     * @param cl  the classloader to add {@link URL} for.
     * @param url the {@link URL} to add.
     */
    public static void addURL(ClassLoader cl, URL url) {
        if (cl instanceof URLClassLoader) {
            addURL0(cl, url);
        } else {
            addURL1(cl, url);
        }
    }

    /**
     * @return running VM version.
     */
    public static int getVmVersion() {
        if (vmVersion < 0) {
            // Check for class version, ez
            String property = System.getProperty("java.class.version", "");
            if (!property.isEmpty())
                return vmVersion = (int) (Float.parseFloat(property) - ClassUtils.VERSION_OFFSET);
            // Odd, not found. Try the spec version
            Logging.LOG.warning("Using fallback vm-version fetch, no value for 'java.class.version'");
            property = System.getProperty("java.vm.specification.version", "");
            if (property.contains("."))
                return vmVersion = (int) Float.parseFloat(property.substring(property.indexOf('.') + 1));
            else if (!property.isEmpty())
                return vmVersion = Integer.parseInt(property);
            // Very odd
            Logging.LOG.warning("Fallback vm-version fetch failed, defaulting to 8");
            return 8;
        }
        return vmVersion;
    }

    private static void addURL0(ClassLoader loader, URL url) {
        Method method;
        try {
            method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException("No 'addURL' method in java.net.URLClassLoader", ex);
        }
        method.setAccessible(true);
        try {
            method.invoke(loader, url);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("'addURL' became inaccessible", ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error adding URL", ex.getTargetException());
        }
    }

    private static void addURL1(ClassLoader loader, URL url) {
        Class<?> currentClass =  loader.getClass();
        do {
            Field field;
            try {
                field = currentClass.getDeclaredField("ucp");
            } catch (NoSuchFieldException ignored) {
                continue;
            }
            field.setAccessible(true);
            Object ucp;
            try {
                ucp = field.get(loader);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("'ucp' became inaccessible", ex);
            }
            String className;
            if (getVmVersion() < 9) {
                className = "sun.misc.URLClassPath";
            } else {
                className = "jdk.internal.misc.URLClassPath";
            }
            Method method;
            try {
                method = Class.forName(className, true, null).getDeclaredMethod("addURL", URL.class);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException("No 'addURL' method in " + className, ex);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(className + " was not found", ex);
            }
            method.setAccessible(true);
            try {
                method.invoke(ucp, url);
                break;
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("'addURL' became inaccessible", ex);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException("Error adding URL", ex.getTargetException());
            }
        } while ((currentClass=currentClass.getSuperclass()) != Object.class);
        throw new IllegalArgumentException("No 'ucp' field in " + loader);
    }

    /**
     * Closes {@link URLClassLoader}.
     *
     * @param loader
     *      Loader to close.
     *
     * @throws IOException
     *      When I/O error occurs.
     */
    public static void close(URLClassLoader loader) throws IOException {
        loader.close();
    }

    /**
     * Sets parent class loader.
     *
     * @param loader
     *      Loader to change parent for.
     * @param parent
     *      New parent loader.
     */
    public static void setParent(ClassLoader loader, ClassLoader parent) {
        Field field;
        try {
            field = ClassLoader.class.getDeclaredField("parent");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("No 'parent' field in java.lang.ClassLoader", ex);
        }
        field.setAccessible(true);
        try {
            field.set(loader, parent);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("'parent' became inaccessible", ex);
        }
    }

    /**
     * Initializes toolkit.
     */
    public static void tkIint() {
        if (getVmVersion() < 9) {
            PlatformImpl.startup(() -> {});
        } else {
            Method m;
            try {
                m = Platform.class.getDeclaredMethod("startup", Runnable.class);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException("javafx.application.Platform.startup(Runnable) is missing", ex);
            }
            m.setAccessible(true);
            try {
                m.invoke(null, (Runnable) () -> {});
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("'startup' became inaccessible", ex);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException("Unable to initialize toolkit", ex.getTargetException());
            }
        }
    }

    /**
     * Patches JDK stuff.
     */
    public static void patch() {
        if (getVmVersion() > 8) {
            openPackages();
            patchReflectionFilters();
        }
    }

    /**
     * Opens all packages.
     */
    private static void openPackages() {
        try {
            Method export = Module.class.getDeclaredMethod("implAddOpens", String.class);
            export.setAccessible(true);
            HashSet<Module> modules = new HashSet<>();
            Class<?> classBase = VMUtils.class;
            Module base = Java9Util.getClassModule(classBase);
            if (base.getLayer() != null)
                modules.addAll(base.getLayer().modules());
            modules.addAll(ModuleLayer.boot().modules());
            for (ClassLoader cl = classBase.getClassLoader(); cl != null; cl = cl.getParent()) {
                modules.add(Java9Util.getLoaderModule(cl));
            }
            for (Module module : modules) {
                for (String name : module.getPackages()) {
                    try {
                        export.invoke(module, name);
                    } catch (Exception ex) {
                        Logging.LOG.log(Level.SEVERE, "Could not export package " + name + " in module " + module, ex);
                    }
                }
            }
        } catch (Exception ex) {
            Logging.LOG.log(Level.SEVERE, "Could not export packages", ex);
        }
    }

    /**
     * Patches reflection filters.
     */
    private static void patchReflectionFilters() {
        Class<?> klass;
        try {
            klass = Class.forName("jdk.internal.reflect.Reflection",
                    true, null);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Unable to locate 'jdk.internal.reflect.Reflection' class", ex);
        }
        try {
            Field[] fields;
            try {
                Method m = Class.class.getDeclaredMethod("getDeclaredFieldsImpl");
                m.setAccessible(true);
                fields = (Field[]) m.invoke(klass);
            } catch (NoSuchMethodException | InvocationTargetException ex) {
                try {
                    Method m = Class.class.getDeclaredMethod("getDeclaredFields0", Boolean.TYPE);
                    m.setAccessible(true);
                    fields = (Field[]) m.invoke(klass, false);
                } catch (InvocationTargetException | NoSuchMethodException ex1) {
                    ex.addSuppressed(ex1);
                    throw new RuntimeException("Unable to get all class fields", ex);
                }
            }
            int c = 0;
            for (Field field : fields) {
                String name = field.getName();
                if ("fieldFilterMap".equals(name) || "methodFilterMap".equals(name)) {
                    field.setAccessible(true);
                    field.set(null, new HashMap<>(0));
                    if (++c == 2) {
                        return;
                    }
                }
            }
            throw new RuntimeException("One of field patches did not apply properly. " +
                    "Expected to patch two fields, but patched: " + c);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Unable to patch reflection filters", ex);
        }
    }
}
