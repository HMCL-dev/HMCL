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
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.util.ExceptionalSupplier;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Logging.LOG;

import java.io.IOException;

public class AuthlibInjectorAccount extends YggdrasilAccount {
    private AuthlibInjectorServer server;
    private ExceptionalSupplier<AuthlibInjectorArtifactInfo, ? extends IOException> authlibInjectorDownloader;

    protected AuthlibInjectorAccount(YggdrasilService service, AuthlibInjectorServer server, ExceptionalSupplier<AuthlibInjectorArtifactInfo, ? extends IOException> authlibInjectorDownloader, String username, UUID characterUUID, YggdrasilSession session) {
        super(service, username, characterUUID, session);

        this.authlibInjectorDownloader = authlibInjectorDownloader;
        this.server = server;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        return inject(super::logIn);
    }

    @Override
    protected AuthInfo logInWithPassword(String password, CharacterSelector selector) throws AuthenticationException {
        return inject(() -> super.logInWithPassword(password, selector));
    }

    private AuthInfo inject(ExceptionalSupplier<AuthInfo, AuthenticationException> loginAction) throws AuthenticationException {
        // Pre-fetch metadata
        GetTask metadataFetchTask = new GetTask(NetworkUtils.toURL(server.getUrl()));
        Thread metadataFetchThread = Lang.thread(() -> {
            try {
                metadataFetchTask.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to pre-fetch Yggdrasil metadata", e);
            }
        }, "Yggdrasil metadata fetch thread");

        // Update authlib-injector
        AuthlibInjectorArtifactInfo artifact;
        try {
            artifact = authlibInjectorDownloader.get();
        } catch (IOException e) {
            throw new AuthenticationException("Failed to download authlib-injector", e);
        }

        // Perform authentication
        AuthInfo info = loginAction.get();
        Arguments arguments = new Arguments().addJVMArguments("-javaagent:" + artifact.getLocation().toString() + "=" + server.getUrl());

        // Wait for metadata to be fetched
        try {
            metadataFetchThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Optional<String> metadata = Optional.ofNullable(metadataFetchTask.getResult());
        if (metadata.isPresent()) {
            arguments = arguments.addJVMArguments(
                    "-Dorg.to2mbn.authlibinjector.config.prefetched=" + Base64.getEncoder().encodeToString(metadata.get().getBytes(UTF_8)));
        }

        return info.withArguments(arguments);
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> map = super.toStorage();
        map.put("serverBaseURL", server.getUrl());
        return map;
    }

    public AuthlibInjectorServer getServer() {
        return server;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), server.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AuthlibInjectorAccount))
            return false;
        AuthlibInjectorAccount another = (AuthlibInjectorAccount) obj;
        return super.equals(another) && server.equals(another.server);
    }
}
