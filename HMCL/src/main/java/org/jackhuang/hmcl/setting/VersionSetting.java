/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import com.google.gson.*;
import javafx.beans.InvalidationListener;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

/**
 *
 * @author huangyuhui
 */
public final class VersionSetting {

    public transient String id;

    private boolean global = false;

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    private final ImmediateBooleanProperty usesGlobalProperty = new ImmediateBooleanProperty(this, "usesGlobal", false);

    public ImmediateBooleanProperty usesGlobalProperty() {
        return usesGlobalProperty;
    }

    /**
     * HMCL Version Settings have been divided into 2 parts.
     * 1. Global settings.
     * 2. Version settings.
     * If a version claims that it uses global settings, its version setting will be disabled.
     *
     * Defaults false because if one version uses global first, custom version file will not be generated.
     */
    public boolean isUsesGlobal() {
        return usesGlobalProperty.get();
    }

    public void setUsesGlobal(boolean usesGlobal) {
        usesGlobalProperty.set(usesGlobal);
    }

    // java

    private final ImmediateStringProperty javaProperty = new ImmediateStringProperty(this, "java", "");

    public ImmediateStringProperty javaProperty() {
        return javaProperty;
    }

    /**
     * Java version or "Custom" if user customizes java directory, "Default" if the jvm that this app relies on.
     */
    public String getJava() {
        return javaProperty.get();
    }

    public void setJava(String java) {
        javaProperty.set(java);
    }

    public boolean isUsesCustomJavaDir() {
        return "Custom".equals(getJava());
    }

    public void setUsesCustomJavaDir() {
        setJava("Custom");
        setDefaultJavaPath(null);
    }

    private final ImmediateStringProperty defaultJavaPathProperty = new ImmediateStringProperty(this, "defaultJavaPath", "");

    /**
     * Path to Java executable, or null if user customizes java directory.
     * It's used to determine which JRE to use when multiple JREs match the selected Java version.
     */
    public String getDefaultJavaPath() {
        return defaultJavaPathProperty.get();
    }

    public void setDefaultJavaPath(String defaultJavaPath) {
        defaultJavaPathProperty.set(defaultJavaPath);
    }

    private final ImmediateStringProperty javaDirProperty = new ImmediateStringProperty(this, "javaDir", "");

    public ImmediateStringProperty javaDirProperty() {
        return javaDirProperty;
    }

    /**
     * User customized java directory or null if user uses system Java.
     */
    public String getJavaDir() {
        return javaDirProperty.get();
    }

    public void setJavaDir(String javaDir) {
        javaDirProperty.set(javaDir);
    }

    private final ImmediateStringProperty wrapperProperty = new ImmediateStringProperty(this, "wrapper", "");

    public ImmediateStringProperty wrapperProperty() {
        return wrapperProperty;
    }

    /**
     * The command to launch java, i.e. optirun.
     */
    public String getWrapper() {
        return wrapperProperty.get();
    }

    public void setWrapper(String wrapper) {
        wrapperProperty.set(wrapper);
    }

    private final ImmediateStringProperty permSizeProperty = new ImmediateStringProperty(this, "permSize", "");

    public ImmediateStringProperty permSizeProperty() {
        return permSizeProperty;
    }

    /**
     * The permanent generation size of JVM garbage collection.
     */
    public String getPermSize() {
        return permSizeProperty.get();
    }

    public void setPermSize(String permSize) {
        permSizeProperty.set(permSize);
    }

    private final ImmediateIntegerProperty maxMemoryProperty = new ImmediateIntegerProperty(this, "maxMemory", OperatingSystem.SUGGESTED_MEMORY);

    public ImmediateIntegerProperty maxMemoryProperty() {
        return maxMemoryProperty;
    }

    /**
     * The maximum memory/MB that JVM can allocate for heap.
     */
    public int getMaxMemory() {
        return maxMemoryProperty.get();
    }

    public void setMaxMemory(int maxMemory) {
        maxMemoryProperty.set(maxMemory);
    }

    /**
     * The minimum memory that JVM can allocate for heap.
     */
    private final ImmediateObjectProperty<Integer> minMemoryProperty = new ImmediateObjectProperty<>(this, "minMemory", null);

    public ImmediateObjectProperty<Integer> minMemoryProperty() {
        return minMemoryProperty;
    }

    public Integer getMinMemory() {
        return minMemoryProperty.get();
    }

    public void setMinMemory(Integer minMemory) {
        minMemoryProperty.set(minMemory);
    }

    private final ImmediateStringProperty preLaunchCommandProperty = new ImmediateStringProperty(this, "precalledCommand", "");

    public ImmediateStringProperty preLaunchCommandProperty() {
        return preLaunchCommandProperty;
    }

    /**
     * The command that will be executed before launching the Minecraft.
     * Operating system relevant.
     */
    public String getPreLaunchCommand() {
        return preLaunchCommandProperty.get();
    }

    public void setPreLaunchCommand(String preLaunchCommand) {
        preLaunchCommandProperty.set(preLaunchCommand);
    }

    // options

    private final ImmediateStringProperty javaArgsProperty = new ImmediateStringProperty(this, "javaArgs", "");

    public ImmediateStringProperty javaArgsProperty() {
        return javaArgsProperty;
    }

    /**
     * The user customized arguments passed to JVM.
     */
    public String getJavaArgs() {
        return javaArgsProperty.get();
    }

    public void setJavaArgs(String javaArgs) {
        javaArgsProperty.set(javaArgs);
    }

    private final ImmediateStringProperty minecraftArgsProperty = new ImmediateStringProperty(this, "minecraftArgs", "");

    public ImmediateStringProperty minecraftArgsProperty() {
        return minecraftArgsProperty;
    }

    /**
     * The user customized arguments passed to Minecraft.
     */
    public String getMinecraftArgs() {
        return minecraftArgsProperty.get();
    }

    public void setMinecraftArgs(String minecraftArgs) {
        minecraftArgsProperty.set(minecraftArgs);
    }

    private final ImmediateBooleanProperty noJVMArgsProperty = new ImmediateBooleanProperty(this, "noJVMArgs", false);

    public ImmediateBooleanProperty noJVMArgsProperty() {
        return noJVMArgsProperty;
    }

    /**
     * True if disallow HMCL use default JVM arguments.
     */
    public boolean isNoJVMArgs() {
        return noJVMArgsProperty.get();
    }

    public void setNoJVMArgs(boolean noJVMArgs) {
        noJVMArgsProperty.set(noJVMArgs);
    }

    private final ImmediateBooleanProperty notCheckGameProperty = new ImmediateBooleanProperty(this, "notCheckGame", false);

    public ImmediateBooleanProperty notCheckGameProperty() {
        return notCheckGameProperty;
    }

    /**
     * True if HMCL does not check game's completeness.
     */
    public boolean isNotCheckGame() {
        return notCheckGameProperty.get();
    }

    public void setNotCheckGame(boolean notCheckGame) {
        notCheckGameProperty.set(notCheckGame);
    }

    private final ImmediateBooleanProperty showLogsProperty = new ImmediateBooleanProperty(this, "showLogs", false);

    public ImmediateBooleanProperty showLogsProperty() {
        return showLogsProperty;
    }

    /**
     * True if show the logs after game launched.
     */
    public boolean isShowLogs() {
        return showLogsProperty.get();
    }

    public void setShowLogs(boolean showLogs) {
        showLogsProperty.set(showLogs);
    }

    // Minecraft settings.

    private final ImmediateStringProperty serverIpProperty = new ImmediateStringProperty(this, "serverIp", "");

    public ImmediateStringProperty serverIpProperty() {
        return serverIpProperty;
    }

    /**
     * The server ip that will be entered after Minecraft successfully loaded immediately.
     *
     * Format: ip:port or without port.
     */
    public String getServerIp() {
        return serverIpProperty.get();
    }

    public void setServerIp(String serverIp) {
        serverIpProperty.set(serverIp);
    }


    private final ImmediateBooleanProperty fullscreenProperty = new ImmediateBooleanProperty(this, "fullscreen", false);

    public ImmediateBooleanProperty fullscreenProperty() {
        return fullscreenProperty;
    }

    /**
     * True if Minecraft started in fullscreen mode.
     */
    public boolean isFullscreen() {
        return fullscreenProperty.get();
    }

    public void setFullscreen(boolean fullscreen) {
        fullscreenProperty.set(fullscreen);
    }

    private final ImmediateIntegerProperty widthProperty = new ImmediateIntegerProperty(this, "width", 854);

    public ImmediateIntegerProperty widthProperty() {
        return widthProperty;
    }

    /**
     * The width of Minecraft window, defaults 800.
     *
     * The field saves int value.
     * String type prevents unexpected value from JsonParseException.
     * We can only reset this field instead of recreating the whole setting file.
     */
    public int getWidth() {
        return widthProperty.get();
    }

    public void setWidth(int width) {
        widthProperty.set(width);
    }


    private final ImmediateIntegerProperty heightProperty = new ImmediateIntegerProperty(this, "height", 480);

    public ImmediateIntegerProperty heightProperty() {
        return heightProperty;
    }

    /**
     * The height of Minecraft window, defaults 480.
     *
     * The field saves int value.
     * String type prevents unexpected value from JsonParseException.
     * We can only reset this field instead of recreating the whole setting file.
     */
    public int getHeight() {
        return heightProperty.get();
    }

    public void setHeight(int height) {
        heightProperty.set(height);
    }

    /**
     * 0 - .minecraft<br/>
     * 1 - .minecraft/versions/&lt;version&gt;/<br/>
     */
    private final ImmediateObjectProperty<EnumGameDirectory> gameDirTypeProperty = new ImmediateObjectProperty<>(this, "gameDirType", EnumGameDirectory.ROOT_FOLDER);

    public ImmediateObjectProperty<EnumGameDirectory> gameDirTypeProperty() {
        return gameDirTypeProperty;
    }

    public EnumGameDirectory getGameDirType() {
        return gameDirTypeProperty.get();
    }

    public void setGameDirType(EnumGameDirectory gameDirType) {
        gameDirTypeProperty.set(gameDirType);
    }

    /**
     * Your custom gameDir
     */
    private final ImmediateStringProperty gameDirProperty = new ImmediateStringProperty(this, "gameDir", "");

    public ImmediateStringProperty gameDirProperty() {
        return gameDirProperty;
    }

    public String getGameDir() {
        return gameDirProperty.get();
    }

    public void setGameDir(String gameDir) {
        gameDirProperty.set(gameDir);
    }

    // launcher settings

    /**
     * 0 - Close the launcher when the game starts.<br/>
     * 1 - Hide the launcher when the game starts.<br/>
     * 2 - Keep the launcher open.<br/>
     */
    private final ImmediateObjectProperty<LauncherVisibility> launcherVisibilityProperty = new ImmediateObjectProperty<>(this, "launcherVisibility", LauncherVisibility.HIDE);

    public ImmediateObjectProperty<LauncherVisibility> launcherVisibilityProperty() {
        return launcherVisibilityProperty;
    }

    public LauncherVisibility getLauncherVisibility() {
        return launcherVisibilityProperty.get();
    }

    public void setLauncherVisibility(LauncherVisibility launcherVisibility) {
        launcherVisibilityProperty.set(launcherVisibility);
    }

    public JavaVersion getJavaVersion() throws InterruptedException {
        // TODO: lazy initialization may result in UI suspension.
        if (StringUtils.isBlank(getJava()))
            setJava(StringUtils.isBlank(getJavaDir()) ? "Default" : "Custom");
        if ("Default".equals(getJava())) return JavaVersion.fromCurrentEnvironment();
        else if (isUsesCustomJavaDir()) {
            try {
                return JavaVersion.fromExecutable(new File(getJavaDir()));
            } catch (IOException e) {
                return null; // Custom Java Directory not found,
            }
        } else if (StringUtils.isNotBlank(getJava())) {
            List<JavaVersion> matchedJava = JavaVersion.getJREs().stream()
                    .filter(java -> java.getVersion().equals(getJava()))
                    .collect(Collectors.toList());
            if (matchedJava.isEmpty()) {
                setJava("Default");
                return JavaVersion.fromCurrentEnvironment();
            } else {
                return matchedJava.stream()
                        .filter(java -> java.getBinary().toString().equals(getDefaultJavaPath()))
                        .findFirst()
                        .orElse(matchedJava.get(0));
            }
        } else throw new Error();
    }

    public void setJavaVersion(JavaVersion java) {
        setJava(java.getVersion());
        setDefaultJavaPath(java.getBinary().toString());
    }

    public void addPropertyChangedListener(InvalidationListener listener) {
        usesGlobalProperty.addListener(listener);
        javaProperty.addListener(listener);
        javaDirProperty.addListener(listener);
        wrapperProperty.addListener(listener);
        permSizeProperty.addListener(listener);
        maxMemoryProperty.addListener(listener);
        minMemoryProperty.addListener(listener);
        preLaunchCommandProperty.addListener(listener);
        javaArgsProperty.addListener(listener);
        minecraftArgsProperty.addListener(listener);
        noJVMArgsProperty.addListener(listener);
        notCheckGameProperty.addListener(listener);
        showLogsProperty.addListener(listener);
        serverIpProperty.addListener(listener);
        fullscreenProperty.addListener(listener);
        widthProperty.addListener(listener);
        heightProperty.addListener(listener);
        gameDirTypeProperty.addListener(listener);
        gameDirProperty.addListener(listener);
        launcherVisibilityProperty.addListener(listener);
        defaultJavaPathProperty.addListener(listener);
    }

    public LaunchOptions toLaunchOptions(File gameDir) throws InterruptedException {
        JavaVersion javaVersion = Optional.ofNullable(getJavaVersion()).orElse(JavaVersion.fromCurrentEnvironment());
        return new LaunchOptions.Builder()
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionName(Metadata.TITLE)
                .setProfileName(Metadata.TITLE)
                .setMinecraftArgs(getMinecraftArgs())
                .setJavaArgs(getJavaArgs())
                .setMaxMemory(getMaxMemory())
                .setMinMemory(getMinMemory())
                .setMetaspace(Lang.toIntOrNull(getPermSize()))
                .setWidth(getWidth())
                .setHeight(getHeight())
                .setFullscreen(isFullscreen())
                .setServerIp(getServerIp())
                .setWrapper(getWrapper())
                .setProxyHost(config().getProxyHost())
                .setProxyPort(config().getProxyPort())
                .setProxyUser(config().getProxyUser())
                .setProxyPass(config().getProxyPass())
                .setPrecalledCommand(getPreLaunchCommand())
                .setNoGeneratedJVMArgs(isNoJVMArgs())
                .create();
    }

    public static class Serializer implements JsonSerializer<VersionSetting>, JsonDeserializer<VersionSetting> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public JsonElement serialize(VersionSetting src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) return JsonNull.INSTANCE;
            JsonObject obj = new JsonObject();

            obj.addProperty("usesGlobal", src.isUsesGlobal());
            obj.addProperty("javaArgs", src.getJavaArgs());
            obj.addProperty("minecraftArgs", src.getMinecraftArgs());
            obj.addProperty("maxMemory", src.getMaxMemory() <= 0 ? OperatingSystem.SUGGESTED_MEMORY : src.getMaxMemory());
            obj.addProperty("minMemory", src.getMinMemory());
            obj.addProperty("permSize", src.getPermSize());
            obj.addProperty("width", src.getWidth());
            obj.addProperty("height", src.getHeight());
            obj.addProperty("javaDir", src.getJavaDir());
            obj.addProperty("precalledCommand", src.getPreLaunchCommand());
            obj.addProperty("serverIp", src.getServerIp());
            obj.addProperty("java", src.getJava());
            obj.addProperty("wrapper", src.getWrapper());
            obj.addProperty("fullscreen", src.isFullscreen());
            obj.addProperty("noJVMArgs", src.isNoJVMArgs());
            obj.addProperty("notCheckGame", src.isNotCheckGame());
            obj.addProperty("showLogs", src.isShowLogs());
            obj.addProperty("gameDir", src.getGameDir());
            obj.addProperty("launcherVisibility", src.getLauncherVisibility().ordinal());
            obj.addProperty("gameDirType", src.getGameDirType().ordinal());
            obj.addProperty("defaultJavaPath", src.getDefaultJavaPath());

            return obj;
        }

        @Override
        public VersionSetting deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json == JsonNull.INSTANCE || !(json instanceof JsonObject))
                return null;
            JsonObject obj = (JsonObject) json;

            int maxMemoryN = parseJsonPrimitive(Optional.ofNullable(obj.get("maxMemory")).map(JsonElement::getAsJsonPrimitive).orElse(null), OperatingSystem.SUGGESTED_MEMORY);
            if (maxMemoryN <= 0) maxMemoryN = OperatingSystem.SUGGESTED_MEMORY;

            VersionSetting vs = new VersionSetting();

            vs.setUsesGlobal(Optional.ofNullable(obj.get("usesGlobal")).map(JsonElement::getAsBoolean).orElse(false));
            vs.setJavaArgs(Optional.ofNullable(obj.get("javaArgs")).map(JsonElement::getAsString).orElse(""));
            vs.setMinecraftArgs(Optional.ofNullable(obj.get("minecraftArgs")).map(JsonElement::getAsString).orElse(""));
            vs.setMaxMemory(maxMemoryN);
            vs.setMinMemory(Optional.ofNullable(obj.get("minMemory")).map(JsonElement::getAsInt).orElse(null));
            vs.setPermSize(Optional.ofNullable(obj.get("permSize")).map(JsonElement::getAsString).orElse(""));
            vs.setWidth(Optional.ofNullable(obj.get("width")).map(JsonElement::getAsJsonPrimitive).map(this::parseJsonPrimitive).orElse(0));
            vs.setHeight(Optional.ofNullable(obj.get("height")).map(JsonElement::getAsJsonPrimitive).map(this::parseJsonPrimitive).orElse(0));
            vs.setJavaDir(Optional.ofNullable(obj.get("javaDir")).map(JsonElement::getAsString).orElse(""));
            vs.setPreLaunchCommand(Optional.ofNullable(obj.get("precalledCommand")).map(JsonElement::getAsString).orElse(""));
            vs.setServerIp(Optional.ofNullable(obj.get("serverIp")).map(JsonElement::getAsString).orElse(""));
            vs.setJava(Optional.ofNullable(obj.get("java")).map(JsonElement::getAsString).orElse(""));
            vs.setWrapper(Optional.ofNullable(obj.get("wrapper")).map(JsonElement::getAsString).orElse(""));
            vs.setGameDir(Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse(""));
            vs.setFullscreen(Optional.ofNullable(obj.get("fullscreen")).map(JsonElement::getAsBoolean).orElse(false));
            vs.setNoJVMArgs(Optional.ofNullable(obj.get("noJVMArgs")).map(JsonElement::getAsBoolean).orElse(false));
            vs.setNotCheckGame(Optional.ofNullable(obj.get("notCheckGame")).map(JsonElement::getAsBoolean).orElse(false));
            vs.setShowLogs(Optional.ofNullable(obj.get("showLogs")).map(JsonElement::getAsBoolean).orElse(false));
            vs.setLauncherVisibility(LauncherVisibility.values()[Optional.ofNullable(obj.get("launcherVisibility")).map(JsonElement::getAsInt).orElse(1)]);
            vs.setGameDirType(EnumGameDirectory.values()[Optional.ofNullable(obj.get("gameDirType")).map(JsonElement::getAsInt).orElse(0)]);
            vs.setDefaultJavaPath(Optional.ofNullable(obj.get("defaultJavaPath")).map(JsonElement::getAsString).orElse(null));

            return vs;
        }

        private int parseJsonPrimitive(JsonPrimitive primitive) {
            return parseJsonPrimitive(primitive, 0);
        }

        private int parseJsonPrimitive(JsonPrimitive primitive, int defaultValue) {
            if (primitive == null)
                return defaultValue;
            else if (primitive.isNumber())
                return primitive.getAsInt();
            else
                return Lang.parseInt(primitive.getAsString(), defaultValue);
        }
    }
}
