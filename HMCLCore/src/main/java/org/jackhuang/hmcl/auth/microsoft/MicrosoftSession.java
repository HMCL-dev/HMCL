package org.jackhuang.hmcl.auth.microsoft;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Pair.pair;

public class MicrosoftSession {
    private final String tokenType;
    private final String accessToken;
    private final User user;
    private final GameProfile profile;

    public MicrosoftSession(String tokenType, String accessToken, User user, GameProfile profile) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
        this.user = user;
        this.profile = profile;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public User getUser() {
        return user;
    }

    public GameProfile getProfile() {
        return profile;
    }

    public static MicrosoftSession fromStorage(Map<?, ?> storage) {
        UUID uuid = tryCast(storage.get("uuid"), String.class).map(UUIDTypeAdapter::fromString).orElseThrow(() -> new IllegalArgumentException("uuid is missing"));
        String name = tryCast(storage.get("displayName"), String.class).orElseThrow(() -> new IllegalArgumentException("displayName is missing"));
        String tokenType = tryCast(storage.get("tokenType"), String.class).orElseThrow(() -> new IllegalArgumentException("tokenType is missing"));
        String accessToken = tryCast(storage.get("accessToken"), String.class).orElseThrow(() -> new IllegalArgumentException("accessToken is missing"));
        String userId = tryCast(storage.get("userid"), String.class).orElseThrow(() -> new IllegalArgumentException("userid is missing"));
        return new MicrosoftSession(tokenType, accessToken, new User(userId), new GameProfile(uuid, name));
    }

    public Map<Object, Object> toStorage() {
        requireNonNull(profile);
        requireNonNull(user);

        return mapOf(
                pair("tokenType", tokenType),
                pair("accessToken", accessToken),
                pair("uuid", UUIDTypeAdapter.fromUUID(profile.getId())),
                pair("displayName", profile.getName()),
                pair("userid", user.id)
        );
    }

    public AuthInfo toAuthInfo() {
        requireNonNull(profile);

        return new AuthInfo(profile.getName(), profile.getId(), accessToken, "{}");
    }

    public static class User {
        private final String id;

        public User(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class GameProfile {
        private final UUID id;
        private final String name;

        public GameProfile(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
