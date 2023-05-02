package org.jackhuang.hmcl.plugin.api;

import org.jackhuang.hmcl.plugin.PluginClassLoader;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@PluginAccessible
public final class PluginInfo {
    private static final Map<String, PluginInfo> runningPluginIds = new ConcurrentHashMap<>();
    private static final Map<Thread, PluginInfo> runningPluginThreads = new ConcurrentHashMap<>();

    public static PluginInfo getCurrentPluginInfo() {
        PluginInfo pluginInfo = runningPluginThreads.get(Thread.currentThread());
        if (pluginInfo != null) {
            return pluginInfo;
        }
        throw new RuntimeException("Not a plugin thread");
    }

    public static boolean checkPluginThread() {
        return runningPluginThreads.containsKey(Thread.currentThread());
    }

    public static Collection<PluginInfo> getPlugins() {
        return runningPluginIds.values();
    }

    public Logger getPluginLogger() {
        return this.logger;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String[] getPluginAuthors() {
        return pluginAuthors;
    }

    public Map<String, String> getPluginEntrypoints() {
        return pluginEntrypoints;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Thread getThread() {
        return thread;
    }

    public File getPluginFile() {
        return ((PluginClassLoader) this.thread.getContextClassLoader()).getPluginFile();
    }

    public PermissionInterface getPermissionInterface() {
        return permissionInterface;
    }

    private final String pluginId;
    private final String pluginName;
    private final String pluginVersion;
    private final String[] pluginAuthors;
    private final Map<String, String> pluginEntrypoints;
    private final List<Throwable> exceptions = new ArrayList<>();

    private boolean enabled = false;

    private Thread thread = null;
    private Logger logger = null;

    private final PermissionInterface permissionInterface = new PermissionInterface();

    public PluginInfo(String pluginId, String pluginName, String pluginVersion, List<String> pluginAuthors, Map<String, String> pluginEntrypoints) {
        if (runningPluginThreads.containsKey(Thread.currentThread())) {
            throw new SecurityException(String.format("Illegal access detected in Thread \"%s\".", Thread.currentThread().getName()));
        }
        this.pluginId = pluginId;
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.pluginAuthors = pluginAuthors.toArray(new String[0]);
        this.pluginEntrypoints = pluginEntrypoints;
    }

    public void enable(Thread pluginThread) {
        if (this.enabled) {
            throw new RuntimeException("This plugin has already been enabled");
        }
        this.thread = pluginThread;
        this.enabled = true;
        this.logger = Logging.LOG;

        runningPluginIds.put(this.pluginId, this);
        runningPluginThreads.put(this.thread, this);
        pluginThread.start();
    }

    public List<Throwable> getExceptions() {
        return this.exceptions;
    }

    public static class PermissionInterface {
        public static class PermissionInterfaceItem {
            private boolean value = false;

            public boolean testPermission() {
                return value;
            }

            public void givePermission() {
                if (PluginInfo.checkPluginThread()) {
                    throw new SecurityException();
                }
                value = true;
            }

            public void revokePermission() {
                value = false;
            }
        }

        public final PermissionInterfaceItem accessDeclaredMembers = new PermissionInterfaceItem();
        public final PermissionInterfaceItem unlimitedWriteFile = new PermissionInterfaceItem();
        public final PermissionInterfaceItem unlimitedReadFile = new PermissionInterfaceItem();
    }
}
