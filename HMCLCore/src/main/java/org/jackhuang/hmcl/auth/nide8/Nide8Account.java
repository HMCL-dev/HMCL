package org.jackhuang.hmcl.auth.nide8;

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class Nide8Account extends Nide8ClassicAccount {

    protected final Nide8Service service;
    protected UUID characterUUID;
    protected final String username;

    private boolean authenticated = false;
    private Nide8Session session;

    protected Nide8Account(Nide8Service service, String serverID, String username, Nide8Session session) {
        super(serverID);
        this.service = requireNonNull(service);
        this.username = requireNonNull(username);
        this.characterUUID = requireNonNull(session.getSelectedProfile().getId());
        this.session = requireNonNull(session);
    }

    protected Nide8Account(Nide8Service service, String serverID, String username, String password) throws AuthenticationException, MalformedURLException {
        super(serverID);
        this.service = requireNonNull(service);
        this.username = requireNonNull(username);

        session = service.authenticate(serverID, username, password, randomClientToken());

        characterUUID = session.getSelectedProfile().getId();
        authenticated = true;
    }

    private static String randomClientToken() {
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return session.getSelectedProfile().getName();
    }

    @Override
    public UUID getUUID() {
        return session.getSelectedProfile().getId();
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!authenticated) {
            try {
                if (service.validate(session.getAccessToken(), session.getClientToken())) {
                    authenticated = true;
                } else {
                    Nide8Session acquiredSession;
                    try {
                        acquiredSession = service.refresh(session.getServerID(), session.getAccessToken(), session.getClientToken(), null);
                    } catch (RemoteAuthenticationException e) {
                        if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                            throw new CredentialExpiredException(e);
                        } else {
                            throw e;
                        }
                    }
                    if (acquiredSession.getSelectedProfile() == null ||
                            !acquiredSession.getSelectedProfile().getId().equals(characterUUID)) {
                        throw new ServerResponseMalformedException("Selected profile changed");
                    }

                    session = acquiredSession;

                    authenticated = true;
                    invalidate();
                }
            } catch (MalformedURLException e) {

            }
        }

        return session.toAuthInfo();
    }

    @Override
    public synchronized Nide8AuthInfo logInWithPassword(String password) throws AuthenticationException {
        try {
            Nide8Session acquiredSession = service.authenticate(serverID, username, password, randomClientToken());

            if (acquiredSession.getSelectedProfile() == null) {
                if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().isEmpty()) {
                    throw new CharacterDeletedException();
                }

                Nide8GameProfile characterToSelect = acquiredSession.getAvailableProfiles().stream()
                        .filter(charatcer -> charatcer.getId().equals(characterUUID))
                        .findFirst()
                        .orElseThrow(CharacterDeletedException::new);

                session = service.refresh(
                        serverID,
                        acquiredSession.getAccessToken(),
                        acquiredSession.getClientToken(),
                        characterToSelect);

            } else {
                if (!acquiredSession.getSelectedProfile().getId().equals(characterUUID)) {
                    throw new CharacterDeletedException();
                }
                session = acquiredSession;
            }

            authenticated = true;
            invalidate();
        } catch (MalformedURLException e) {

        }
        return session.toAuthInfo();
    }

    @Override
    public AuthInfo playOffline() {
        return session.toAuthInfo();
    }

    @Override
    public Map<Object, Object> toStorage() {
        return session.toStorage();
    }

    public Nide8Service getService() {
        return service;
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return BindingMapping.of(service.getProfileRepository()
                        .binding(new Nide8LoginObj(session.getServerID(), getUUID())))
                .map(profile -> profile.flatMap(it -> {
                    try {
                        return Nide8Service.getTextures(it);
                    } catch (ServerResponseMalformedException e) {
                        LOG.log(Level.WARNING, "Failed to parse texture payload", e);
                        return Optional.empty();
                    }
                }));
    }

    @Override
    public void clearCache() {
        authenticated = false;
    }

    @Override
    public String toString() {
        return "Nide8Account[uuid=" + characterUUID + ", name=" + getCharacter() + "]";
    }

    @Override
    public int hashCode() {
        return characterUUID.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Nide8Account that = (Nide8Account) o;
        return characterUUID.equals(that.characterUUID);
    }
}