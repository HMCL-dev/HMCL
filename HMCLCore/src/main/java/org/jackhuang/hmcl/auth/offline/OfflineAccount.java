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
package org.jackhuang.hmcl.auth.offline;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactInfo;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloadException;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 *
 * @author huang
 */
public class OfflineAccount extends Account {

    private final AuthlibInjectorArtifactProvider downloader;
    private final String username;
    private final UUID uuid;
    private final Map<TextureType, Texture> textures;

    protected OfflineAccount(AuthlibInjectorArtifactProvider downloader, String username, UUID uuid, Map<TextureType, Texture> textures) {
        this.downloader = requireNonNull(downloader);
        this.username = requireNonNull(username);
        this.uuid = requireNonNull(uuid);
        this.textures = textures;

        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return username;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        AuthInfo authInfo = new AuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), "{}");

        if (skin != null || cape != null) {
            CompletableFuture<AuthlibInjectorArtifactInfo> artifactTask = CompletableFuture.supplyAsync(() -> {
                try {
                    return downloader.getArtifactInfo();
                } catch (IOException e) {
                    throw new CompletionException(new AuthlibInjectorDownloadException(e));
                }
            });

            AuthlibInjectorArtifactInfo artifact;
            try {
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

            try {
                YggdrasilServer server = new YggdrasilServer(0);
                server.start();
                server.addCharacter(new YggdrasilServer.Character(uuid, username, TextureModel.STEVE,
                        mapOf(
                                pair(TextureType.SKIN, server.loadTexture(skin)),
                                pair(TextureType.CAPE, server.loadTexture(cape))
                        )));

                return authInfo.withArguments(new Arguments().addJVMArguments(
                                "-javaagent:" + artifact.getLocation().toString() + "=" + "http://localhost:" + server.getListeningPort(),
                                "-Dauthlibinjector.side=client"
                        ))
                        .withCloseable(server::stop);
            } catch (IOException e) {
                throw new AuthenticationException(e);
            }
        } else {
            return authInfo;
        }
    }

    @Override
    public AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return logIn();
    }

    @Override
    public Optional<AuthInfo> playOffline() throws AuthenticationException {
        return Optional.of(logIn());
    }

    @Override
    public Map<Object, Object> toStorage() {
        return mapOf(
                pair("uuid", UUIDTypeAdapter.fromUUID(uuid)),
                pair("username", username),
                pair("skin", skin),
                pair("cape", cape)
        );
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("uuid", uuid)
                .append("skin", skin)
                .append("cape", cape)
                .toString();
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OfflineAccount))
            return false;
        OfflineAccount another = (OfflineAccount) obj;
        return username.equals(another.username);
    }
}
