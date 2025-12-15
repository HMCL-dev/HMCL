/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonSerializable
public final class AuthlibInjectorServers implements Validation {

    public static final String CONFIG_FILENAME = "authlib-injectors.json";

    private static final Set<AuthlibInjectorServer> servers = new CopyOnWriteArraySet<>();

    public static Set<AuthlibInjectorServer> getServers() {
        return servers;
    }

    private final List<String> urls;

    private AuthlibInjectorServers(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (this.urls == null) {
            throw new JsonParseException("authlib-injectors.json -> urls cannot be null.");
        }
    }

    public static void init() {
        Path configLocation;
        Path jarPath = JarUtils.thisJarPath();
        if (jarPath != null && Files.isRegularFile(jarPath) && Files.isWritable(jarPath)) {
            configLocation = jarPath.getParent().resolve(CONFIG_FILENAME);
        } else {
            configLocation = Paths.get(CONFIG_FILENAME);
        }

        if (ConfigHolder.isNewlyCreated() && Files.exists(configLocation)) {
            AuthlibInjectorServers configInstance;
            try {
                configInstance = JsonUtils.fromJsonFile(configLocation, AuthlibInjectorServers.class);
            } catch (IOException | JsonParseException e) {
                LOG.warning("Malformed authlib-injectors.json", e);
                return;
            }

            if (!configInstance.urls.isEmpty()) {
                config().setPreferredLoginType(Accounts.getLoginType(Accounts.FACTORY_AUTHLIB_INJECTOR));
                for (String url : configInstance.urls) {
                    Task.supplyAsync(Schedulers.io(), () -> AuthlibInjectorServer.locateServer(url))
                            .thenAcceptAsync(Schedulers.javafx(), server -> {
                                config().getAuthlibInjectorServers().add(server);
                                servers.add(server);
                            })
                            .start();
                }
            }
        }
    }
}
