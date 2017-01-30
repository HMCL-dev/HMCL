/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.setting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author jack
 */
public class ServerProperties {

    public static ServerProperties getInstance() {
        return instance;
    }

    private static ServerProperties instance;

    public static void init(String path) {
        instance = new ServerProperties(path);
    }

    String path;
    InputStream is;
    Properties p;

    public ServerProperties(String path) {
        this.path = path;
    }

    public String getProperty(String key) {
        return getProperty(key, "");
    }

    public String getProperty(String key, String defaultValue) {
        try {
            is = FileUtils.openInputStream(new File(path, "server.properties"));
            p = new Properties();
            p.load(is);
            return p.getProperty(key, defaultValue);
        } catch (IOException ex) {
            HMCLog.warn("Failed to get property in server.properties", ex);
            return "";
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public int getPropertyInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getPropertyBoolean(String key, boolean defaultValue) {
        return getProperty(key, String.valueOf(defaultValue)).equals("true");
    }

    public void setProperty(String key, String value) {
        try {
            is = FileUtils.openInputStream(new File(path, "server.properties"));
            p = new Properties();
            p.load(is);
            p.setProperty(key, value);
            SimpleDateFormat f = new SimpleDateFormat("E M d HH:mm:ss z y");
            p.store(FileUtils.openOutputStream(new File(path, "server.properties")),
                    "Minecraft server properties\n" + f.format(new Date()));
        } catch (IOException ex) {
            HMCLog.warn("Failed to set property in server.properties", ex);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public void setProperty(String key, boolean value) {
        setProperty(key, value ? "true" : "false");
    }

    public void setProperty(String key, int value) {
        setProperty(key, String.valueOf(value));
    }

    public void setGeneratorSettings(String string) {
        setProperty("generator-settings", string);
    }

    public void setAllowNether(boolean bool) {
        setProperty("allow-nether", bool);
    }

    public void setLevelName(String string) {
        setProperty("level-name", string);
    }

    public void setEnableQuery(boolean bool) {
        setProperty("enable-query", bool);
    }

    public void setAllowFlight(boolean bool) {
        setProperty("allow-flight", bool);
    }

    public void setServerPort(int integer) {
        setProperty("server-port", integer);
    }

    public void setLevelType(String string) {
        setProperty("level-type", string);
    }

    public void setEnableRcon(boolean bool) {
        setProperty("enable-rcon", bool);
    }

    public void setForceGameMode(boolean bool) {
        setProperty("force-gamemode", bool);
    }

    public void setLevelSeed(String string) {
        setProperty("level-seed", string);
    }

    public void setServerIP(String string) {
        setProperty("server-ip", string);
    }

    public void setMaxBuildHeight(int integer) {
        setProperty("max-build-height", integer);
    }

    public void setSpawnNPCs(boolean bool) {
        setProperty("spawn-npcs", bool);
    }

    public void setWhiteList(boolean bool) {
        setProperty("white-list", bool);
    }

    public void setSpawnAnimals(boolean bool) {
        setProperty("spawn-animals", bool);
    }

    public void setTexturePack(String string) {
        setProperty("texture-pack", string);
    }

    public void setSnooperEnabled(boolean bool) {
        setProperty("snooper-enabled", bool);
    }

    public void setHardCore(boolean bool) {
        setProperty("hardcore", bool);
    }

    public void setOnlineMode(boolean bool) {
        setProperty("online-mode", bool);
    }

    public void setPVP(boolean bool) {
        setProperty("pvp", bool);
    }

    public void setDifficulty(int integer) {
        setProperty("difficulty", integer);
    }

    public void setServerName(String string) {
        setProperty("server-name", string);
    }

    public void setGameMode(int integer) {
        setProperty("gamemode", integer);
    }

    public void setMaxPlayers(int integer) {
        setProperty("max-players", integer);
    }

    public void setSpawnMonsters(boolean bool) {
        setProperty("spawn-monsters", bool);
    }

    public void setViewDistence(int integer) {
        setProperty("view-distance", integer);
    }

    public void setGenerateStructures(boolean bool) {
        setProperty("generate-structures", bool);
    }

    public void setMotd(String string) {
        setProperty("motd", string);
    }

    public static String getDefault() {
        return "generator-settings=\n"
               + "op-permission-level=4\n"
               + "allow-nether=true\n"
               + "level-name=world\n"
               + "enable-query=false\n"
               + "allow-flight=false\n"
               + "announce-player-achievements=true\n"
               + "server-port=25565\n"
               + "level-type=DEFAULT\n"
               + "enable-rcon=false\n"
               + "force-gamemode=false\n"
               + "level-seed=\n"
               + "server-ip=\n"
               + "max-build-height=256\n"
               + "spawn-npcs=true\n"
               + "white-list=false\n"
               + "spawn-animals=true\n"
               + "hardcore=false\n"
               + "snooper-enabled=true\n"
               + "online-mode=false\n"
               + "resource-pack=\n"
               + "pvp=true\n"
               + "difficulty=1\n"
               + "server-name=Unknown Server\n"
               + "enable-command-block=false\n"
               + "gamemode=0\n"
               + "player-idle-timeout=0\n"
               + "max-players=20\n"
               + "spawn-monsters=true\n"
               + "generate-structures=true\n"
               + "view-distance=10\n"
               + "spawn-protection=16\n"
               + "motd=A Minecraft Server";
    }

}
