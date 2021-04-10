package org.jackhuang.hmcl.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Package-private util to deal with modules.
 *
 * @author xxDark
 */
final class Java9Util {

    private static final MethodHandle CLASS_MODULE;
    private static final MethodHandle CLASS_LOADER_MDOULE;

    /**
     * Deny all constructions.
     */
    private Java9Util() {
    }

    /**
     * @param klass {@link Class} to get module from.
     * @return {@link Module} of the class.
     */
    static Module getClassModule(Class<?> klass) {
        try {
            return (Module) CLASS_MODULE.invokeExact(klass);
        } catch (Throwable t) {
            // That should never happen.
            throw new AssertionError(t);
        }
    }


    /**
     * @param loader {@link ClassLoader} to get module from.
     * @return {@link Module} of the class.
     */
    static Module getLoaderModule(ClassLoader loader) {
        try {
            return (Module) CLASS_LOADER_MDOULE.invokeExact(loader);
        } catch (Throwable t) {
            // That should never happen.
            throw new AssertionError(t);
        }
    }

    static {
        try {
            Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
            field.setAccessible(true);
            MethodHandles.publicLookup();
            Lookup lookup = (Lookup) field.get(null);
            MethodType type = MethodType.methodType(Module.class);
            CLASS_MODULE = lookup.findVirtual(Class.class, "getModule", type);
            CLASS_LOADER_MDOULE = lookup.findVirtual(ClassLoader.class, "getUnnamedModule", type);
        } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
