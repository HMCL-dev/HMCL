/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AuthlibInjectorAccount extends YggdrasilAccount {
    private final AuthlibInjectorServer server;
    private AuthlibInjectorArtifactProvider downloader;

    public AuthlibInjectorAccount(AuthlibInjectorServer server, AuthlibInjectorArtifactProvider downloader, String username, String password, CharacterSelector selector) throws AuthenticationException {
        super(server.getYggdrasilService(), username, password, selector);
        this.server = server;
        this.downloader = downloader;
    }

    public AuthlibInjectorAccount(AuthlibInjectorServer server, AuthlibInjectorArtifactProvider downloader, String username, YggdrasilSession session) {
        super(server.getYggdrasilService(), username, session);
        this.server = server;
        this.downloader = downloader;
    }

    @Override
    public synchronized AuthInfo logIn() throws AuthenticationException {
        return inject(super::logIn);
    }

    @Override
    public synchronized AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return inject(() -> super.logInWithPassword(password));
    }

    @Override
    public Optional<AuthInfo> playOffline() {
        Optional<AuthInfo> auth = super.playOffline();
        Optional<AuthlibInjectorArtifactInfo> artifact = downloader.getArtifactInfoImmediately();
        Optional<String> prefetchedMeta = server.getMetadataResponse();

        if (auth.isPresent() && artifact.isPresent() && prefetchedMeta.isPresent()) {
            return Optional.of(auth.get().withArguments(generateArguments(artifact.get(), server, prefetchedMeta.get())));
        } else {
            return Optional.empty();
        }
    }

    private AuthInfo inject(ExceptionalSupplier<AuthInfo, AuthenticationException> loginAction) throws AuthenticationException {
        CompletableFuture<String> prefetchedMetaTask = CompletableFuture.supplyAsync(() -> {
            try {
                return server.fetchMetadataResponse();
            } catch (IOException e) {
                throw new CompletionException(new ServerDisconnectException(e));
            }
        });

        CompletableFuture<AuthlibInjectorArtifactInfo> artifactTask = CompletableFuture.supplyAsync(() -> {
            try {
                return downloader.getArtifactInfo();
            } catch (IOException e) {
                throw new CompletionException(new AuthlibInjectorDownloadException(e));
            }
        });

        AuthInfo auth = loginAction.get();
        String prefetchedMeta;
        AuthlibInjectorArtifactInfo artifact;

        try {
            prefetchedMeta = prefetchedMetaTask.get();
            artifact = artifactTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AuthenticationException) {
                throw (AuthenticationException) e.getCause();
            } else {
                throw new AuthenticationException(e.getCause());
            }
        }

        return auth.withArguments(generateArguments(artifact, server, prefetchedMeta));
    }

    private static Arguments generateArguments(AuthlibInjectorArtifactInfo artifact, AuthlibInjectorServer server, String prefetchedMeta) {
        return new Arguments().addJVMArguments(
                "-javaagent:" + artifact.getLocation().toString() + "=" + server.getUrl(),
                "-Dauthlibinjector.side=client",
                "-Dauthlibinjector.yggdrasil.prefetched=" + Base64.getEncoder().encodeToString(prefetchedMeta.getBytes(UTF_8)));
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> map = super.toStorage();
        map.put("serverBaseURL", server.getUrl());
        return map;
    }

    @Override
    public void clearCache() {
        super.clearCache();
        server.invalidateMetadataCache();
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

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", getUsername())
                .append("server", getServer())
                .toString();
    }
}
