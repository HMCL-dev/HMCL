package org.jackhuang.hmcl.plugin;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.plugin.api.IPluginSecurityManager;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.plugin.api.PluginUnsafeInterface;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.File;
import java.io.FilePermission;
import java.lang.reflect.ReflectPermission;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.Permission;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class PluginSecurityManager extends SecurityManager implements IPluginSecurityManager {
    private static final PluginSecurityManager instance = new PluginSecurityManager();

    private static final Map<Class<? extends Permission>, BiConsumer<PluginInfo, Permission>> pluginSecurityRules;

    private static final Set<File> pluginAccessibleFiles;

    private static final ThreadLocal<Boolean> securityManagerState = ThreadLocal.withInitial(() -> true);

    public void disable() {
        if (!PluginInfo.checkPluginThread()) {
            throw new RuntimeException("Not a plugin thread.");
        }

        PluginUnsafeInterface.checkCallerClassPermission();
        securityManagerState.set(false);
    }

    public void enable() {
        if (!PluginInfo.checkPluginThread()) {
            throw new RuntimeException("Not a plugin thread.");
        }

        PluginUnsafeInterface.checkCallerClassPermission();
        securityManagerState.set(true);
    }

    private static class PluginSecurityRulesBuilder {
        private final Map<Class<? extends Permission>, BiConsumer<PluginInfo, Permission>> rules = new HashMap<>();

        @SuppressWarnings("unchecked")
        public <T extends Permission> PluginSecurityRulesBuilder addRule(Class<T> permissionClass, BiConsumer<PluginInfo, T> consumer) {
            rules.put(permissionClass, (BiConsumer<PluginInfo, Permission>) consumer);
            return this;
        }

        public Map<Class<? extends Permission>, BiConsumer<PluginInfo, Permission>> build() {
            return rules;
        }
    }

    private static class PluginAccessibleFilesBuilder {
        private final Set<File> pluginAllowReadingFiles = new HashSet<>();

        public PluginAccessibleFilesBuilder() {
            CodeSource codeSource = PluginSecurityManager.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path path = null;
                try {
                    path = Paths.get(codeSource.getLocation().toURI());
                } catch (FileSystemNotFoundException | IllegalArgumentException | URISyntaxException ignored) {
                }

                if (path != null) {
                    this.addFile(path.toFile().getAbsoluteFile());
                }
            }

            if (JarUtils.thisJar().isPresent()) {
                this.addFile(JarUtils.thisJar().get().toFile().getAbsoluteFile());
            }
        }

        public PluginAccessibleFilesBuilder addFile(File file) {
            if (file != null) {
                if (pluginAllowReadingFiles.contains(file)) {
                    return this;
                }
                if (file.isDirectory()) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        Arrays.stream(files).forEach(this::addFile);
                    }
                }

                pluginAllowReadingFiles.add(file.getAbsoluteFile());
            }
            return this;
        }

        public Set<File> build() {
            return pluginAllowReadingFiles;
        }
    }

    static {
        pluginAccessibleFiles = new PluginAccessibleFilesBuilder().build();

        pluginSecurityRules = new PluginSecurityRulesBuilder()
                .addRule(FilePermission.class, (pluginInfo, permission) -> {
                    switch (permission.getActions()) {
                        case "read": {
                            File currentFile = new File(permission.getName());
                            if (pluginInfo.getPluginFile().equals(currentFile)) {
                                break;
                            }
                            if (pluginAccessibleFiles.contains(currentFile)) {
                                break;
                            }
                            throwSecurityException(permission, pluginInfo.getPermissionInterface().unlimitedReadFile);
                            break;
                        }
                        case "write": {
                            throwSecurityException(permission, pluginInfo.getPermissionInterface().unlimitedWriteFile);
                            break;
                        }
                        default: {
                            throwSecurityException(permission);
                        }
                    }
                })
                .addRule(RuntimePermission.class, (pluginInfo, permission) -> {
                    switch (permission.getName()) {
                        case "getClassLoader": {
                            break;
                        }
                        case "accessDeclaredMembers": {
                            StackTraceElement[] stackTraceElements = new RuntimeException().getStackTrace();

                            if (stackTraceElements.length >= 3) {
                                StackTraceElement callerClass = stackTraceElements[2];

                                if (callerClass.getClassName().equals("java.lang.Class") && callerClass.getMethodName().equals("checkMemberAccess")) {
                                    if (stackTraceElements.length >= 5) {
                                        String reflectCallerClassName = stackTraceElements[4].getClassName();

                                        if (reflectCallerClassName.startsWith("java.") || reflectCallerClassName.startsWith("jdk.") || reflectCallerClassName.startsWith("org.jackhuang.hmcl.")) {
                                            if (reflectCallerClassName.startsWith("java.reflect.")) {
                                                // WTF? Are you using reflect to invoke reflect? Sus
                                                throwSecurityException(permission);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }

                            throwSecurityException(permission, pluginInfo.getPermissionInterface().accessDeclaredMembers);
                            break;
                        }
                        default: {
                            throwSecurityException(permission);
                        }
                    }
                })
                .addRule(ReflectPermission.class, (pluginInfo, permission) -> {
                    switch (permission.getName()) {
                        case "suppressAccessChecks": {
                            StackTraceElement[] stackTraceElements = new RuntimeException().getStackTrace();

                            if (stackTraceElements.length >= 3) {
                                StackTraceElement callerClass = stackTraceElements[2];

                                if (callerClass.getClassName().equals("java.lang.reflect.AccessibleObject") && callerClass.getMethodName().equals("checkPermission")) {
                                    if (stackTraceElements.length >= 5) {
                                        String reflectCallerClassName = stackTraceElements[4].getClassName();

                                        if (reflectCallerClassName.startsWith("java.") || reflectCallerClassName.startsWith("jdk.") || reflectCallerClassName.startsWith("org.jackhuang.hmcl.")) {
                                            if (reflectCallerClassName.startsWith("java.reflect.")) {
                                                // WTF? Are you using reflect to invoke reflect? Sus
                                                throwSecurityException(permission);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }

                            throwSecurityException(permission, pluginInfo.getPermissionInterface().accessDeclaredMembers);
                            break;
                        }
                        default: {
                            throwSecurityException(permission);
                        }
                    }
                })
                .build();
    }

    private PluginSecurityManager() {
    }

    public static void initialize() {
        if (System.getSecurityManager() != null) {
            throw new SecurityException("SecurityManager has already been enabled.");
        }
        System.setSecurityManager(instance);
    }

    private static void throwSecurityException(Permission permission) throws SecurityException {
        PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.WARNING, "Permission not allowed.");
        throw new SecurityException(String.format("Permission \"%s\"[name=\"%s\",action=\"%s\"] denied.", permission.getClass().getName(), permission.getName(), permission.getActions()));
    }

    private static void throwSecurityException(Permission permission, PluginInfo.PermissionInterface.PermissionInterfaceItem permissionInterfaceItem) throws SecurityException {
        if (!permissionInterfaceItem.testPermission()) {
            throwSecurityException(permission);
        }
    }

    @Override
    public void checkPermission(Permission permission) {
        if (PluginInfo.checkPluginThread() && securityManagerState.get()) {
            PluginInfo pluginInfo = PluginInfo.getCurrentPluginInfo();
            LOG.log(Level.INFO, String.format("Plugin \"%s\" tries to get premission %s", pluginInfo.getPluginId(), permission.toString()));
            BiConsumer<PluginInfo, Permission> rule = pluginSecurityRules.get(permission.getClass());
            if (rule != null) {
                rule.accept(pluginInfo, permission);
            } else {
                throwSecurityException(permission);
            }
        }
    }

    @Override
    public ThreadGroup getThreadGroup() {
        checkPermission(new RuntimePermission("getThreadGroup"));
        return super.getThreadGroup();
    }

    @Override
    public void checkExec(String cmd) {
        checkPermission(new RuntimePermission("executeCommand", cmd));
    }

    @Override
    public void checkAccess(Thread t) {
        checkPermission(new RuntimePermission("modifyThread"));
    }

    @Override
    public void checkAccess(ThreadGroup g) {
        checkPermission(new RuntimePermission("modifyThreadGroup"));
    }

    @Override
    public void checkExit(int status) {
        checkPermission(new RuntimePermission("exitVM", Integer.toString(status)));
    }

    @Override
    public void checkLink(String lib) {
        if (lib == null) {
            throw new NullPointerException("library can't be null");
        }
        checkPermission(new RuntimePermission("loadLibrary", lib));
    }
}
