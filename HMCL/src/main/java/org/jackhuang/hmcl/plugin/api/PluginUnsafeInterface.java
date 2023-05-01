package org.jackhuang.hmcl.plugin.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

@PluginAccessible
public final class PluginUnsafeInterface {
    private PluginUnsafeInterface() {
    }

    private static String getCallerClass() {
        StackTraceElement[] stackTraceElements = new RuntimeException().getStackTrace();
        if (stackTraceElements.length >= 3) {
            return stackTraceElements[2].getClassName();
        } else {
            return "";
        }
    }

    public static void checkCallerClassPermission() {
        String callerClass = getCallerClass();
        if (!callerClass.startsWith("org.jackhuang.hmcl.")) {
            throw new SecurityException(String.format("Class %s doesn't have the permission to access HMCL Native.", callerClass));
        }
    }

    private static void disableSecurityManager() {
        try {
            ((IPluginSecurityManager) System.getSecurityManager()).disable();
        } catch (Throwable e) {
            try {
                PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.WARNING, "Fail to disable SecurityManager in plugin thread", e);
            } catch (Throwable ignored) {
            }
            try {
                ((IPluginSecurityManager) System.getSecurityManager()).enable();
            } catch (Throwable ignored) {
                PluginInfo.getCurrentPluginInfo().getThread().stop();
            }
            throw e;
        }
    }

    private static void enableSecurityManager() {
        try {
            ((IPluginSecurityManager) System.getSecurityManager()).enable();
        } catch (Throwable e) {
            try {
                PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.WARNING, "Fail to enable SecurityManager in plugin thread", e);
            } catch (Throwable ignored) {
            }
            PluginInfo.getCurrentPluginInfo().getThread().stop();
            throw e;
        }
    }

    public static <T> T runUnsafe(Supplier<T> supplier) {
        if (!PluginInfo.checkPluginThread()) {
            throw new RuntimeException("Not a plugin thread.");
        }

        checkCallerClassPermission();

        disableSecurityManager();

        T res;
        try {
            res = supplier.get();
        } catch (Throwable e) {
            enableSecurityManager();
            throw e;
        }

        enableSecurityManager();
        return res;
    }

    public static <T> CompletableFuture<T> runUnsafeAsync(Supplier<T> supplier) {
        if (!PluginInfo.checkPluginThread()) {
            throw new RuntimeException("Not a plugin thread.");
        }

        checkCallerClassPermission();

        disableSecurityManager();

        CompletableFuture<T> res = CompletableFuture.supplyAsync(supplier);

        enableSecurityManager();
        return res;
    }
}
