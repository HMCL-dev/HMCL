package org.jackhuang.hmcl.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.plugin.api.IPluginEvents;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class PluginManager {
    private PluginManager() {
    }

    private static final Gson CONFIG_GSON = new GsonBuilder().registerTypeAdapter(PluginInfo.class, new PluginGsonTypeAdapterV1()).create();

    private static class PluginThread extends Thread {
        private IPluginEvents eventsHandler = null;
        private final PluginInfo pluginInfo;

        private final Queue<BiConsumer<PluginInfo, IPluginEvents>> eventQueue = new ConcurrentLinkedQueue<>();

        public PluginThread(File pluginFile, PluginInfo pluginInfo) {
            this.setUncaughtExceptionHandler(this::pluginThreadUncaughtExceptionHandler);

            this.setContextClassLoader(PluginClassLoader.of(pluginFile));
            this.setName(pluginInfo.getPluginName());
            this.pluginInfo = pluginInfo;
        }

        // HMCL Thread
        public synchronized void putEvent(BiConsumer<PluginInfo, IPluginEvents> callback) {
            this.eventQueue.add(callback);

            this.notify();
        }

        private static void checkSecurityManager() {
            boolean checkSecurityManager = true;
            try {
                System.getSecurityManager().checkPermission(new RuntimePermission("setSecurityManager"));
                checkSecurityManager = false;
            } catch (SecurityException ignored) {
            }

            if (!checkSecurityManager) {
                throw new SecurityException("PluginSecurityManager isn't enabled.");
            }
        }

        private void setupEventsHandler() {
            String threadMain = PluginInfo.getCurrentPluginInfo().getPluginEntrypoints().get("events");
            if (threadMain != null) {
                try {
                    Class<?> threadMainClass = Class.forName(threadMain, true, Thread.currentThread().getContextClassLoader());

                    if (!IPluginEvents.class.isAssignableFrom(threadMainClass)) {
                        throw new RuntimeException("Class announced as the thread_main entrypoint must implements PluginMainThreadEntrypoint.");
                    }

                    Constructor<?>[] constructors = threadMainClass.getConstructors();
                    for (Constructor<?> constructor : constructors) {
                        if (constructor.getParameters().length == 0) {
                            this.eventsHandler = (IPluginEvents) constructor.newInstance();
                            break;
                        }
                    }
                    if (this.eventsHandler == null) {
                        throw new RuntimeException("Class announced as the events entrypoint must produce public <init>().");
                    }
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                         InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void run() {
            checkSecurityManager();
            setupEventsHandler();

            try {
                this.eventsHandler.onPluginLoad();
            } catch (Throwable throwable) {
                pluginInfo.getPluginLogger().log(Level.INFO, String.format("Uncaught exception of event:onPluginLoad in plugin %s", pluginInfo.getPluginId()), throwable);
            }

            while (true) {
                try {
                    synchronized (this) {
                        this.wait();
                    }
                } catch (InterruptedException ignored) {
                }

                while (true) {
                    BiConsumer<PluginInfo, IPluginEvents> callback = this.eventQueue.poll();
                    if (callback == null) {
                        break;
                    }
                    try {
                        callback.accept(pluginInfo, this.eventsHandler);
                    } catch (Throwable throwable) {
                        pluginInfo.getPluginLogger().log(Level.INFO, String.format("Uncaught exception of queue in plugin %s", pluginInfo.getPluginId()), throwable);
                    }
                }
            }
        }

        private void pluginThreadUncaughtExceptionHandler(Thread thread, Throwable throwable) {
            pluginInfo.getPluginLogger().log(Level.WARNING, String.format("Uncaught exception in plugin %s", pluginInfo.getPluginId()), throwable);
        }
    }

    public static void initialize() {
        PluginSecurityManager.initialize();
    }

    public static void enablePluginFromFile(@NotNull File pluginFile) {
        if (!pluginFile.exists()) {
            LOG.log(Level.WARNING, String.format("Plugin file \"%s\" not found.", pluginFile.getAbsolutePath()), new FileNotFoundException(String.format("Plugin file \"%s\" not found.", pluginFile.getAbsolutePath())));
        }

        PluginInfo pluginInfo = readPluginInfoFromFile(pluginFile);

        if (pluginInfo == null) {
            return;
        }

        Thread pluginThread = new PluginThread(pluginFile, pluginInfo);
        LOG.log(Level.INFO, String.format("Enable plugin %s %s in thread \"%s\".", pluginInfo.getPluginId(), pluginInfo.getPluginVersion(), pluginThread.getName()));
        pluginInfo.enable(pluginThread);
    }

    public static PluginInfo readPluginInfoFromFile(File pluginFile) {
        String configString = readPluginConfigFile(pluginFile);
        if (configString == null) {
            return null;
        }

        return parseConfigString(configString);
    }

    private static String readPluginConfigFile(File pluginFile) {
        if (!pluginFile.exists() || !pluginFile.canRead() || !pluginFile.getAbsolutePath().endsWith(".jar")) {
            return null;
        }

        String configString = null;

        try (ZipFile zipFile = new ZipFile(pluginFile.getAbsolutePath())) {
            Enumeration<? extends ZipEntry> inputEntries = zipFile.entries();
            while (inputEntries.hasMoreElements()) {
                ZipEntry zipEntry = inputEntries.nextElement();
                if (zipEntry == null) {
                    break;
                }

                if (zipEntry.getName().equals("hmcl.plugin.json")) {
                    configString = IOUtils.readFullyAsString(zipFile.getInputStream(zipEntry));
                    break;
                }
            }
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Fail to parse plugin file.", e);
            return null;
        }

        return configString;
    }

    public static PluginInfo parseConfigString(String configString) {
        try {
            return CONFIG_GSON.fromJson(configString, PluginInfo.class);
        } catch (JsonParseException e) {
            LOG.log(Level.WARNING, "Fail to parse plugin config file.", e);
            return null;
        }
    }

    public static void sendEvent(BiConsumer<PluginInfo, IPluginEvents> callback) {
        PluginInfo.getPlugins().forEach(pluginInfo -> sendEvent(callback, pluginInfo));
    }

    public static void sendEvent(BiConsumer<PluginInfo, IPluginEvents> callback, PluginInfo pluginInfo) {
        ((PluginThread) pluginInfo.getThread()).putEvent(callback);
    }
}
