/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

/**
 * A {@code ClassLoader} to support mod loading.
 *
 * <p> {@code BootWrapperClassLoader} inherits {@code URLClassLoader}
 * and supports loading JAR mods under certain directories (defaults
 * to {@code mods/} under current working directory). </p>
 * @author azure-bluet
 */
public class BootWrapperClassLoader extends URLClassLoader {
    private static final String MODDIR_SYSTEM_PROPERTY = "hmcl.moddir";
    private static final String DEFAULT_MODDIR = "mods";
    private static final String MANIFEST_MODID_ENTRY = "HMCL-Mod-ID";
    private static final String MANIFEST_ENTRYPOINT_ENTRY = "HMCL-Mod-Entrypoint";

    private static BootWrapperClassLoader INSTANCE = null;

    /**
     * Get the CL instance.
     * @return The {@code BootWrapperClassLoader} instance, or null if non has been initialized
     */
    public static BootWrapperClassLoader getInstance() {
        return INSTANCE;
    }

    /**
     * Represents the current mod loading state.
     */
    public enum ModInitializationState {
        // Mods haven't been initialized
        NOT_INITIALIZED,
        // Mods have been initialized
        INITIALIZED,
        // No mods have been found
        NO_MODS_FOUND,
        // Mods initialization has failed
        INITIALIZATION_FAILED
    }
    private ModInitializationState state = ModInitializationState.NOT_INITIALIZED;
    /**
     * Get the current state
     * @return Current loading state
     */
    public ModInitializationState getState() {
        return state;
    }

    /**
     * Get whether HMCL is currently modded
     * @return Modded or not
     */
    public boolean isModded() {
        return state == ModInitializationState.INITIALIZED;
    }

    private final Set<String> failedMods = new HashSet<>();
    private final Map<String, Object> succeededMods = new HashMap<>();

    /**
     * Get all the mods that failed to load
     * @return A {@code Set<String>} containing IDs of mods that failed to load
     */
    public Set<String> getFailedMods() {
        return Set.copyOf(failedMods);
    }
    /**
     * Get all the mods that loaded successfully
     * @return A {@code Set<String>} containing IDs of mods that loaded successfully
     */
    public Map<String, Object> getSucceededMods() {
        return Map.copyOf(succeededMods);
    }

    BootWrapperClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        INSTANCE = this;
    }

    private static File getModDir() {
        String property = System.getProperty(MODDIR_SYSTEM_PROPERTY);
        if (property != null) return new File(property);
        else return new File(System.getProperty("user.dir"), DEFAULT_MODDIR);
    }

    void safeInitialize() {
        try {
            initialize();
        } catch (IOException e) {
            state = ModInitializationState.INITIALIZATION_FAILED;
        }
    }
    private void initialize() throws IOException {
        // Mod directory
        File modDir = getModDir();
        if (!modDir.exists() || !modDir.isDirectory()) return;

        // Resolve mods
        Set<URI> uris = new HashSet<>();
        Map<String, String> entrypoints = new HashMap<>();
        File[] files = modDir.listFiles();
        if (files == null || files.length == 0) {
            state = ModInitializationState.NO_MODS_FOUND;
            return;
        }
        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                final Manifest manifest = jar.getManifest();
                if (manifest == null) continue;
                final String modid = manifest.getMainAttributes().getValue(MANIFEST_MODID_ENTRY);
                final String entrypoint = manifest.getMainAttributes().getValue(MANIFEST_ENTRYPOINT_ENTRY);
                if (entrypoint != null) {
                    entrypoints.put(modid, entrypoint);
                    uris.add(file.toURI());
                }
            } catch (ZipException ignored) {}
        }

        if (uris.isEmpty()) {
            // If no mods were found, vanilla HMCL is launched without any notifications
            state = ModInitializationState.NO_MODS_FOUND;
            return;
        }

        for (URI uri : uris) super.addURL(uri.toURL());
        // Execute entrypoints
        for (String modid : entrypoints.keySet()) {
            String entrypoint = entrypoints.get(modid);
            try {
                Class<?> clazz = this.loadClass(entrypoint);
                Constructor<?> constructor = clazz.getConstructor();
                Object mod = constructor.newInstance();
                succeededMods.put(modid, mod);
            } catch (ReflectiveOperationException e) {
                failedMods.add(modid);
            }
        }

        state = ModInitializationState.INITIALIZED;
    }

    private final Set<BiFunction<String, byte[], byte[]>> transformers = new HashSet<>();
    /**
     * Register a bytecode transformer.
     * @param transformer A {@code BiFunction} representing the transformer
     */
    public void registerTransformer(BiFunction<String, byte[], byte[]> transformer) {
        if (transformer == null) return;
        transformers.add(transformer);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            InputStream is = this.getResourceAsStream(name.replace('.', '/') + ".class");
            if (is == null) throw new ClassNotFoundException(String.format("Could not find class %s", name));
            byte[] buffer = is.readAllBytes();
            is.close();
            for (var transformer : transformers) {
                try {
                    byte[] transformed = transformer.apply(name, buffer);
                    if (transformed != null) buffer =  transformed;
                } catch (Throwable ignored) {
                    // TODO: maybe log this exception?
                }
            }
            return defineClass(name, buffer, 0, buffer.length);
        } catch (IOException | NullPointerException e) {
            throw new ClassNotFoundException("Error loading class " + name, e);
        }
    }
}
