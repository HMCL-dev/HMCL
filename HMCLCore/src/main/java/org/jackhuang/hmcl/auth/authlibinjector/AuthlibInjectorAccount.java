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
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

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
    public AuthInfo playOffline() throws AuthenticationException {
        AuthInfo auth = super.playOffline();
        Optional<AuthlibInjectorArtifactInfo> artifact = downloader.getArtifactInfoImmediately();
        Optional<String> prefetchedMeta = server.getMetadataResponse();

        if (artifact.isPresent() && prefetchedMeta.isPresent()) {
            return new AuthlibInjectorAuthInfo(auth, artifact.get(), server, prefetchedMeta.get());
        } else {
            throw new NotLoggedInException();
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

        return new AuthlibInjectorAuthInfo(auth, artifact, server, prefetchedMeta);
    }

    private static class AuthlibInjectorAuthInfo extends AuthInfo {

        private final AuthlibInjectorArtifactInfo artifact;
        private final AuthlibInjectorServer server;
        private final String prefetchedMeta;

        public AuthlibInjectorAuthInfo(AuthInfo authInfo, AuthlibInjectorArtifactInfo artifact, AuthlibInjectorServer server, String prefetchedMeta) {
            super(authInfo.getUsername(), authInfo.getUUID(), authInfo.getAccessToken(), authInfo.getUserType(), authInfo.getUserProperties());

            this.artifact = artifact;
            this.server = server;
            this.prefetchedMeta = prefetchedMeta;
        }

        @Override
        public Arguments getLaunchArguments(LaunchOptions options) {
            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.getLocation().toString() + "=" + server.getUrl(),
                    "-Dauthlibinjector.side=client",
                    "-Dauthlibinjector.yggdrasil.prefetched=" + Base64.getEncoder().encodeToString(prefetchedMeta.getBytes(UTF_8)));
        }
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
    public String getIdentifier() {
        return server.getUrl() + ":" + super.getIdentifier();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), server.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != AuthlibInjectorAccount.class)
            return false;
        AuthlibInjectorAccount another = (AuthlibInjectorAccount) obj;
        return isPortable() == another.isPortable()
                && characterUUID.equals(another.characterUUID) && server.equals(another.server);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("uuid", characterUUID)
                .append("username", getUsername())
                .append("server", getServer().getUrl())
                .toString();
    }

    public static Set<TextureType> getUploadableTextures(CompleteGameProfile profile) {
        String prop = profile.getProperties().get("uploadableTextures");
        if (prop == null)
            return emptySet();
        Set<TextureType> result = EnumSet.noneOf(TextureType.class);
        for (String val : prop.split(",")) {
            val = val.toUpperCase(Locale.ROOT);
            TextureType parsed;
            try {
                parsed = TextureType.valueOf(val);
            } catch (IllegalArgumentException e) {
                continue;
            }
            result.add(parsed);
        }
        return unmodifiableSet(result);
    }
}
