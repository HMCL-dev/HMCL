/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.littleskin;

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuthAccount;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class LittleSkinAccount extends OAuthAccount {
    private final LittleSkinService service;
    private LittleSkinSession session;
    private final UUID characterUUID;

    private boolean authenticated = false;

    LittleSkinAccount(LittleSkinService service) throws AuthenticationException {
        this(service, service.authenticate());
    }

    LittleSkinAccount(LittleSkinService service, LittleSkinSession session) {
        this.service = service;
        this.session = session;
        this.characterUUID = session.getIdToken().getSelectedProfile().getId();
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!authenticated) {
            if (service.validate(session)) {
                authenticated = true;
            } else {
                LittleSkinSession acquiredSession = service.refresh(session);
                if (!Objects.equals(acquiredSession.getIdToken().getSelectedProfile().getId(), characterUUID)) {
                    throw new ServerResponseMalformedException("Selected profile changed");
                }

                session = acquiredSession;

                authenticated = true;
                invalidate();
            }
        }

        return session.toAuthInfo();
    }

    @Override
    public AuthInfo logInWhenCredentialsExpired() throws AuthenticationException {
        LittleSkinSession acquiredSession = service.authenticate();

        if (acquiredSession.getIdToken() == null) {
            session = service.refresh(acquiredSession);
        } else {
            session = acquiredSession;
        }

        authenticated = true;
        invalidate();
        return session.toAuthInfo();
    }

    @Override
    public String getCharacter() {
        return session.getIdToken().getSelectedProfile().getName();
    }

    @Override
    public UUID getUUID() {
        return characterUUID;
    }

    @Override
    public AuthInfo playOffline() {
        return session.toAuthInfo();
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return BindingMapping.of(service.getProfileRepository().binding(getUUID()))
                .map(profile -> profile.flatMap(it -> {
                    try {
                        return YggdrasilService.getTextures(it);
                    } catch (ServerResponseMalformedException e) {
                        LOG.warning("Failed to parse texture payload", e);
                        return Optional.empty();
                    }
                }));
    }

    @Override
    public boolean canUploadSkin() {
        return true;
    }

    @Override
    public void uploadSkin(boolean isSlim, Path file) throws AuthenticationException, UnsupportedOperationException {
        service.uploadSkin(characterUUID, session.getAccessToken(), isSlim, file);
    }

    @Override
    public Map<Object, Object> toStorage() {
        return session.toStorage();
    }

    @Override
    public String getIdentifier() {
        return "";
    }
}
