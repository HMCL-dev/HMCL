package org.jackhuang.hmcl.util;

import com.nqzero.permit.Permit;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;

import static java.lang.Class.forName;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static org.jackhuang.hmcl.Metadata.HMCL_DIRECTORY;
import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * Utility for patching self when missing dependencies.
 * Copy from https://github.com/Col-E/Recaf/blob/master/src/main/java/me/coley/recaf/util/self/SelfDependencyPatcher.java
 *
 * @author Matt
 */
public class SelfDependencyPatcher {
    private static final Path DEPENDENCIES_DIR_PATH = HMCL_DIRECTORY.resolve("dependencies");
    private static final Map<Integer, List<String>> JFX_DEPENDENCIES = new LinkedHashMap<Integer, List<String>>(5, 1F) {
        {
            put(15, Arrays.asList(
                    jfxUrl("swing", "15.0.1"),
                    jfxUrl("media", "15.0.1"),
                    jfxUrl("fxml", "15.0.1"),
                    jfxUrl("web", "15.0.1"),
                    jfxUrl("controls", "15.0.1"),
                    jfxUrl("graphics", "15.0.1"),
                    jfxUrl("base", "15.0.1")
            ));
            put(14, Arrays.asList(
                    jfxUrl("swing", "14.0.2"),
                    jfxUrl("media", "14.0.2"),
                    jfxUrl("fxml", "14.0.2"),
                    jfxUrl("web", "14.0.2"),
                    jfxUrl("controls", "14.0.2"),
                    jfxUrl("graphics", "14.0.2"),
                    jfxUrl("base", "14.0.2")
            ));
            put(13, Arrays.asList(
                    jfxUrl("swing", "13.0.2"),
                    jfxUrl("media", "13.0.2"),
                    jfxUrl("fxml", "13.0.2"),
                    jfxUrl("web", "13.0.2"),
                    jfxUrl("controls", "13.0.2"),
                    jfxUrl("graphics", "13.0.2"),
                    jfxUrl("base", "13.0.2")
            ));
            put(12, Arrays.asList(
                    jfxUrl("swing", "12.0.2"),
                    jfxUrl("media", "12.0.2"),
                    jfxUrl("fxml", "12.0.2"),
                    jfxUrl("web", "12.0.2"),
                    jfxUrl("controls", "12.0.2"),
                    jfxUrl("graphics", "12.0.2"),
                    jfxUrl("base", "12.0.2")
            ));
            put(11, Arrays.asList(
                    jfxUrl("swing", "11.0.2"),
                    jfxUrl("media", "11.0.2"),
                    jfxUrl("fxml", "11.0.2"),
                    jfxUrl("web", "11.0.2"),
                    jfxUrl("controls", "11.0.2"),
                    jfxUrl("graphics", "11.0.2"),
                    jfxUrl("base", "11.0.2")
            ));
        }
    };

    /**
     * Patch in any missing dependencies, if any.
     */
    public static void patch() {
        if (getVmVersion() > 8) {
            Permit.godMode();
            Permit.unLog();
            patchReflectionFilters();
        }
        // Do nothing if JavaFX is detected
        try {
            try {
                forName("javafx.application.Platform", false, ClassLoader.getSystemClassLoader());
                return;
            } catch(Exception ignored) {
            }
        } catch(UnsupportedClassVersionError error) {
            // Loading the JavaFX class was unsupported.
            // We are probably on 8 and its on 11
            showIncompatibleVersion();
            return;
        }
        // So the problem with Java 8 is that some distributions DO NOT BUNDLE JAVAFX
        // Why is this a problem? OpenJFX does not come in public bundles prior to Java 11
        // So you're out of luck unless you change your JDK or update Java.
        if (getVmVersion() < 11) {
            showIncompatibleVersion();
            return;
        }
        // Otherwise we're free to download in Java 11+
        LOG.info("Missing JavaFX dependencies, attempting to patch in missing classes");
        // Check if dependencies need to be downloaded
        if (!hasCachedDependencies()) {
            LOG.info(" - No local cache, downloading dependencies...");
            try {
                fetchDependencies();
            } catch(IOException ex) {
                logError(ex, "Failed to download dependencies!");
                System.exit(-1);
            }
        } else {
            LOG.info(" - Local cache found!");
        }
        // Add the dependencies
        try {
            loadFromCache();
        } catch(IOException ex) {
            logError(ex, ex.getMessage());
            System.exit(-1);
        } catch(ReflectiveOperationException ex) {
            logError(ex, "Failed to add dependencies to classpath!");
            System.exit(-1);
        }
        LOG.info(" - Done!");
    }


    /**
     * Inject them into the current classpath.
     *
     * @throws IOException
     * 		When the locally cached dependency urls cannot be resolved.
     * @throws ReflectiveOperationException
     * 		When the call to add these urls to the system classpath failed.
     */
    private static void loadFromCache() throws IOException, ReflectiveOperationException {
        LOG.info(" - Loading dependencies...");
        // Get Jar URLs
        List<URL> jarUrls = new ArrayList<>();
        Files.walk(DEPENDENCIES_DIR_PATH).forEach(path -> {
            try {
                jarUrls.add(path.toUri().toURL());
            } catch(MalformedURLException ex) {
                logError(ex, "Failed to convert '%s' to URL", path.toFile().getAbsolutePath());
            }
        });
        // Fetch UCP of application's ClassLoader
        // - ((ClassLoaders.AppClassLoader) ClassLoaders.appClassLoader()).ucp
        Class<?> clsClassLoaders = Class.forName("jdk.internal.loader.ClassLoaders");
        Object appClassLoader = clsClassLoaders.getDeclaredMethod("appClassLoader").invoke(null);
        Class<?> ucpOwner = appClassLoader.getClass();
        // Field removed in 16, but still exists in parent class "BuiltinClassLoader"
        if (getVmVersion() >= 16)
            ucpOwner = ucpOwner.getSuperclass();
        Field fieldUCP = ucpOwner.getDeclaredField("ucp");
        fieldUCP.setAccessible(true);
        Object ucp = fieldUCP.get(appClassLoader);
        Class<?> clsUCP = ucp.getClass();
        Method addURL = clsUCP.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);
        // Add each jar.
        for(URL url : jarUrls)
            addURL.invoke(ucp, url);
    }

    /**
     * Display a message detailing why self-patching cannot continue.
     */
    private static void showIncompatibleVersion() {
        String message = "Application cannot self-patch below Java 11 on this JVM. " +
                "Please run using JDK 11 or higher or use a JDK that bundles JavaFX.\n" +
                " - Your JDK does not bundle JavaFX\n" +
                " - Downloadable JFX bundles only come with 11 support or higher.";
        showMessageDialog(null, message, "Error: Cannot self-patch", ERROR_MESSAGE);
        // LOG and exit
        LOG.severe(message);
        System.exit(-1);
    }

    /**
     * Download dependencies.
     *
     * @throws IOException
     * 		When the files cannot be fetched or saved.
     */
    private static void fetchDependencies() throws IOException {
        // Get dir to store dependencies in
        Path dependenciesDir = DEPENDENCIES_DIR_PATH;
        if (!Files.isDirectory(dependenciesDir)) {
            Files.createDirectories(dependenciesDir);
        }
        // Download each dependency
        List<String> dependencies = getLatestDependencies();
        for(String dependencyPattern : dependencies) {
            String dependencyUrlPath = String.format(dependencyPattern, getMvnName());
            URL depURL = new URL(dependencyUrlPath);
            Path dependencyFilePath = DEPENDENCIES_DIR_PATH.resolve(getFileName(dependencyUrlPath));
            Files.copy(depURL.openStream(), dependencyFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * @return {@code true} when the dependencies directory has files in it.
     */
    private static boolean hasCachedDependencies() {
        String[] files = DEPENDENCIES_DIR_PATH.toFile().list();
        if (files == null)
            return false;
        return files.length >= getLatestDependencies().size();
    }

    /**
     * @param url
     * 		Full url path.
     *
     * @return Name of file at url.
     */
    private static String getFileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * @param component
     * 		Name of the component.
     *
     * @return Formed URL for the component.
     */
    private static String jfxUrl(String component, String version) {
        // Add platform specific identifier to the end.
        // https://repo1.maven.org/maven2/org/openjfx/javafx-%s/%s/javafx-%s-%s
        return String.format("https://maven.aliyun.com/repository/central/org/openjfx/javafx-%s/%s/javafx-%s-%s",
                component, version, component, version) + "-%s.jar";
    }

    /**
     * @return Latest JavaFX supported version for.
     */
    private static int getLatestSupportedJfxVersion() {
        int version = getVmVersion();
        while (version >= 11) {
            List<String> dependencies = JFX_DEPENDENCIES.get(version);
            if (dependencies != null)
                return version;
            version--;
        }
        throw new AssertionError("Failed to get latest supported JFX version");
    }

    /**
     * @return JavaFX dependencies list for the current VM version.
     */
    private static List<String> getLatestDependencies() {
        int version = getLatestSupportedJfxVersion();
        if (version >= 11) {
            return JFX_DEPENDENCIES.get(version);
        }
        throw new AssertionError("Failed to get latest JFX artifact urls");
    }

    private static void logError(Throwable t, String msg, Object... args) {
        LOG.log(Level.SEVERE, t, () -> compile(msg, args));
    }

    /**
     * Compiles message with "{}" arg patterns.
     *
     * @param msg
     * 		Message pattern.
     * @param args
     * 		Values to pass.
     *
     * @return Compiled message with inlined arg values.
     */
    private static String compile(String msg, Object[] args) {
        int c = 0;
        while(msg.contains("{}")) {
            // Failsafe, shouldn't occur if logging is written correctly
            if (c == args.length)
                return msg;
            // Replace arg in pattern
            Object arg = args[c];
            String argStr = arg == null ? "null" : arg.toString();
            msg = msg.replaceFirst("\\{}", Matcher.quoteReplacement(argStr));
            c++;
        }
        return msg;
    }

    private static String getMvnName() {
        switch (OperatingSystem.CURRENT_OS) {
            case LINUX: return "linux";
            case OSX: return "mac";
            default: return "win";
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

    private static int vmVersion = -1;

    /**
     * @return running VM version.
     */
    public static int getVmVersion() {
        if (vmVersion < 0) {
            // Check for class version, ez
            String property = System.getProperty("java.class.version", "");
            if (!property.isEmpty())
                return vmVersion = (int) (Float.parseFloat(property) - 44);
            // Odd, not found. Try the spec version
            LOG.warning("Using fallback vm-version fetch, no value for 'java.class.version'");
            property = System.getProperty("java.vm.specification.version", "");
            if (property.contains("."))
                return vmVersion = (int) Float.parseFloat(property.substring(property.indexOf('.') + 1));
            else if (!property.isEmpty())
                return vmVersion = Integer.parseInt(property);
            // Very odd
            LOG.warning("Fallback vm-version fetch failed, defaulting to 8");
            return 8;
        }
        return vmVersion;
    }
}
