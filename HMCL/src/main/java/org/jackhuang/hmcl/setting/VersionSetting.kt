/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.setting

import com.google.gson.*
import javafx.beans.InvalidationListener
import javafx.beans.property.*
import org.jackhuang.hmcl.MainApplication
import org.jackhuang.hmcl.game.LaunchOptions
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

class VersionSetting() {

    var isGlobal: Boolean = false

    /**
     * HMCL Version Settings have been divided into 2 parts.
     * 1. Global settings.
     * 2. Version settings.
     * If a version claims that it uses global settings, its version setting will be disabled.
     *
     * Defaults false because if one version uses global first, custom version file will not be generated.
     */
    val usesGlobalProperty = SimpleBooleanProperty(this, "usesGlobal", false)
    var usesGlobal: Boolean by usesGlobalProperty

    // java

    /**
     * Java version or null if user customizes java directory.
     */
    val javaProperty = SimpleStringProperty(this, "java", null)
    var java: String? by javaProperty

    /**
     * User customized java directory or null if user uses system Java.
     */
    val javaDirProperty = SimpleStringProperty(this, "javaDir", "")
    var javaDir: String by javaDirProperty

    /**
     * The command to launch java, i.e. optirun.
     */
    val wrapperProperty = SimpleStringProperty(this, "wrapper", "")
    var wrapper: String by wrapperProperty

    /**
     * The permanent generation size of JVM garbage collection.
     */
    val permSizeProperty = SimpleStringProperty(this, "permSize", "")
    var permSize: String by permSizeProperty

    /**
     * The maximum memory that JVM can allocate.
     * The size of JVM heap.
     */
    val maxMemoryProperty = SimpleIntegerProperty(this, "maxMemory", OS.SUGGESTED_MEMORY)
    var maxMemory: Int by maxMemoryProperty

    /**
     * The command that will be executed before launching the Minecraft.
     * Operating system relevant.
     */
    val precalledCommandProperty = SimpleStringProperty(this, "precalledCommand", "")
    var precalledCommand: String by precalledCommandProperty

    // options

    /**
     * The user customized arguments passed to JVM.
     */
    val javaArgsProperty = SimpleStringProperty(this, "javaArgs", "")
    var javaArgs: String by javaArgsProperty

    /**
     * The user customized arguments passed to Minecraft.
     */
    val minecraftArgsProperty = SimpleStringProperty(this, "minecraftArgs", "")
    var minecraftArgs: String by minecraftArgsProperty

    /**
     * True if disallow HMCL use default JVM arguments.
     */
    val noJVMArgsProperty = SimpleBooleanProperty(this, "noJVMArgs", false)
    var noJVMArgs: Boolean by noJVMArgsProperty

    /**
     * True if HMCL does not check game's completeness.
     */
    val notCheckGameProperty = SimpleBooleanProperty(this, "notCheckGame", false)
    var notCheckGame: Boolean by notCheckGameProperty

    // Minecraft settings.

    /**
     * The server ip that will be entered after Minecraft successfully loaded immediately.
     *
     * Format: ip:port or without port.
     */
    val serverIpProperty = SimpleStringProperty(this, "serverIp", "")
    var serverIp: String by serverIpProperty

    /**
     * True if Minecraft started in fullscreen mode.
     */
    val fullscreenProperty = SimpleBooleanProperty(this, "fullscreen", false)
    var fullscreen: Boolean by fullscreenProperty

    /**
     * The width of Minecraft window, defaults 800.
     *
     * The field saves int value.
     * String type prevents unexpected value from causing JsonSyntaxException.
     * We can only reset this field instead of recreating the whole setting file.
     */
    val widthProperty = SimpleIntegerProperty(this, "width", 854)
    var width: Int by widthProperty


    /**
     * The height of Minecraft window, defaults 480.
     *
     * The field saves int value.
     * String type prevents unexpected value from causing JsonSyntaxException.
     * We can only reset this field instead of recreating the whole setting file.
     */
    val heightProperty = SimpleIntegerProperty(this, "height", 480)
    var height: Int by heightProperty


    /**
     * 0 - .minecraft<br/>
     * 1 - .minecraft/versions/&lt;version&gt;/<br/>
     */
    val gameDirTypeProperty = SimpleObjectProperty<EnumGameDirectory>(this, "gameDirTypeProperty", EnumGameDirectory.ROOT_FOLDER)
    var gameDirType: EnumGameDirectory by gameDirTypeProperty

    // launcher settings

    /**
     * 0 - Close the launcher when the game starts.<br/>
     * 1 - Hide the launcher when the game starts.<br/>
     * 2 - Keep the launcher open.<br/>
     */
    val launcherVisibilityProperty = SimpleObjectProperty<LauncherVisibility>(this, "launcherVisibility", LauncherVisibility.HIDE)
    var launcherVisibility: LauncherVisibility by launcherVisibilityProperty

    fun addPropertyChangedListener(listener: InvalidationListener) {
        usesGlobalProperty.addListener(listener)
        javaProperty.addListener(listener)
        javaDirProperty.addListener(listener)
        wrapperProperty.addListener(listener)
        permSizeProperty.addListener(listener)
        maxMemoryProperty.addListener(listener)
        precalledCommandProperty.addListener(listener)
        javaArgsProperty.addListener(listener)
        minecraftArgsProperty.addListener(listener)
        noJVMArgsProperty.addListener(listener)
        notCheckGameProperty.addListener(listener)
        serverIpProperty.addListener(listener)
        fullscreenProperty.addListener(listener)
        widthProperty.addListener(listener)
        heightProperty.addListener(listener)
        gameDirTypeProperty.addListener(listener)
        launcherVisibilityProperty.addListener(listener)
    }

    @Throws(IOException::class)
    fun toLaunchOptions(gameDir: File): LaunchOptions {
        return LaunchOptions(
                gameDir = gameDir,
                java = if (java == null) JavaVersion.fromCurrentEnvironment()
                       else JavaVersion.fromExecutable(File(java)),
                versionName = MainApplication.TITLE,
                profileName = MainApplication.TITLE,
                minecraftArgs = minecraftArgs,
                javaArgs = javaArgs,
                maxMemory = maxMemory,
                metaspace = permSize.toIntOrNull(),
                width = width,
                height = height,
                fullscreen = fullscreen,
                serverIp = serverIp,
                wrapper = wrapper,
                proxyHost = Settings.SETTINGS.proxyHost,
                proxyPort = Settings.SETTINGS.proxyPort,
                proxyUser = Settings.SETTINGS.proxyUserName,
                proxyPass = Settings.SETTINGS.proxyPassword,
                precalledCommand = precalledCommand,
                noGeneratedJVMArgs = noJVMArgs
        )
    }

    companion object Serializer: JsonSerializer<VersionSetting>, JsonDeserializer<VersionSetting> {
        override fun serialize(src: VersionSetting?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            if (src == null) return JsonNull.INSTANCE
            val jsonObject = JsonObject()
            with(jsonObject) {
                addProperty("usesGlobal", src.usesGlobal)
                addProperty("javaArgs", src.javaArgs)
                addProperty("minecraftArgs", src.minecraftArgs)
                addProperty("maxMemory", src.maxMemory)
                addProperty("permSize", src.permSize)
                addProperty("width", src.width)
                addProperty("height", src.height)
                addProperty("javaDir", src.javaDir)
                addProperty("precalledCommand", src.precalledCommand)
                addProperty("serverIp", src.serverIp)
                addProperty("java", src.java)
                addProperty("wrapper", src.wrapper)
                addProperty("fullscreen", src.fullscreen)
                addProperty("noJVMArgs", src.noJVMArgs)
                addProperty("notCheckGame", src.notCheckGame)
                addProperty("launcherVisibility", src.launcherVisibility.ordinal)
                addProperty("gameDirType", src.gameDirType.ordinal)
            }

            return jsonObject
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): VersionSetting? {
            if (json == null || json == JsonNull.INSTANCE || json !is JsonObject) return null

            return VersionSetting().apply {
                usesGlobal = json["usesGlobal"]?.asBoolean ?: false
                javaArgs = json["javaArgs"]?.asString ?: ""
                minecraftArgs = json["minecraftArgs"]?.asString ?: ""
                maxMemory = parseJsonPrimitive(json["maxMemory"]?.asJsonPrimitive)
                permSize = json["permSize"]?.asString ?: ""
                width = parseJsonPrimitive(json["width"]?.asJsonPrimitive)
                height = parseJsonPrimitive(json["height"]?.asJsonPrimitive)
                javaDir = json["javaDir"]?.asString ?: ""
                precalledCommand = json["precalledCommand"]?.asString ?: ""
                serverIp = json["serverIp"]?.asString ?: ""
                java = json["java"]?.asString
                wrapper = json["wrapper"]?.asString ?: ""
                fullscreen = json["fullscreen"]?.asBoolean ?: false
                noJVMArgs = json["noJVMArgs"]?.asBoolean ?: false
                notCheckGame = json["notCheckGame"]?.asBoolean ?: false
                launcherVisibility = LauncherVisibility.values()[json["launcherVisibility"]?.asInt ?: 1]
                gameDirType = EnumGameDirectory.values()[json["gameDirType"]?.asInt ?: 0]
            }
        }

        fun parseJsonPrimitive(primitive: JsonPrimitive?, defaultValue: Int = 0): Int {
            if (primitive != null)
                if (primitive.isNumber)
                    return primitive.asInt
                else
                    return primitive.asString.toIntOrNull() ?: defaultValue
            else
                return defaultValue
        }

    }
}