package org.jackhuang.hmcl.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * @author Glavo
 */
public final class Pack200Utils {
    private Pack200Utils() {
    }

    private static final String[] IMPL_NAMES = {
            "java.util.jar.Pack200",
            "org.glavo.pack200.Pack200",
            "io.pack200.Pack200"
    };

    private static final MethodHandle newUnpackerHandle;
    private static final MethodHandle unpackHandle;
    private static final MethodHandle unpackFileHandle;

    static {
        Class<?> pack200Class = null;
        Class<?> unpackerClass = null;

        for (String implName : IMPL_NAMES) {
            try {
                pack200Class = Class.forName(implName);
                unpackerClass = Class.forName(implName + "$Unpacker");
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (pack200Class == null) {
            LOG.warning("Pack200 not found");
            newUnpackerHandle = null;
            unpackHandle = null;
            unpackFileHandle = null;
        } else {
            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle newUnpacker = null;
            MethodHandle unpack = null;
            MethodHandle unpackFile = null;
            try {
                newUnpacker = lookup.findStatic(pack200Class, "newUnpacker", MethodType.methodType(unpackerClass));
                unpack = lookup.findVirtual(unpackerClass, "unpack", MethodType.methodType(void.class, InputStream.class, JarOutputStream.class));
                unpackFile = lookup.findVirtual(unpackerClass, "unpack", MethodType.methodType(void.class, File.class, JarOutputStream.class));
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "Failed to find pack200 methods", e);
            }

            if (newUnpacker != null) {
                newUnpackerHandle = newUnpacker;
                unpackHandle = unpack;
                unpackFileHandle = unpackFile;
            } else {
                newUnpackerHandle = null;
                unpackHandle = null;
                unpackFileHandle = null;
            }
        }

    }

    public static boolean isSupported() {
        return newUnpackerHandle != null;
    }

    public static void unpack(InputStream in, JarOutputStream out) throws IOException {
        if (newUnpackerHandle == null) {
            throw new UnsupportedOperationException("Pack200");
        }

        try {
            unpackHandle.invoke(newUnpackerHandle.invoke(), in, out);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void unpack(File in, JarOutputStream out) throws IOException {
        if (newUnpackerHandle == null) {
            throw new UnsupportedOperationException("Pack200");
        }

        try {
            unpackFileHandle.invoke(newUnpackerHandle.invoke(), in, out);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
