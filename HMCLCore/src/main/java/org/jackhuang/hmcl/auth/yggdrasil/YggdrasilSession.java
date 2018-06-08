package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Pair.pair;

public class YggdrasilSession {

    private String clientToken;
    private String accessToken;
    private GameProfile selectedProfile;
    private GameProfile[] availableProfiles;
    private User user;

    public YggdrasilSession(String clientToken, String accessToken, GameProfile selectedProfile, GameProfile[] availableProfiles, User user) {
        this.clientToken = clientToken;
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
        this.availableProfiles = availableProfiles;
        this.user = user;
    }

    public String getClientToken() {
        return clientToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * @return nullable (null if no character is selected)
     */
    public GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    /**
     * @return nullable (null if the YggdrasilSession is loaded from storage)
     */
    public GameProfile[] getAvailableProfiles() {
        return availableProfiles;
    }

    public User getUser() {
        return user;
    }

    public static YggdrasilSession fromStorage(Map<?, ?> storage) {
        UUID uuid = tryCast(storage.get("uuid"), String.class).map(UUIDTypeAdapter::fromString).orElseThrow(() -> new IllegalArgumentException("uuid is missing"));
        String name = tryCast(storage.get("displayName"), String.class).orElseThrow(() -> new IllegalArgumentException("displayName is missing"));
        String clientToken = tryCast(storage.get("clientToken"), String.class).orElseThrow(() -> new IllegalArgumentException("clientToken is missing"));
        String accessToken = tryCast(storage.get("accessToken"), String.class).orElseThrow(() -> new IllegalArgumentException("accessToken is missing"));
        String userId = tryCast(storage.get("userid"), String.class).orElseThrow(() -> new IllegalArgumentException("userid is missing"));
        PropertyMap userProperties = tryCast(storage.get("userProperties"), Map.class).map(PropertyMap::fromMap).orElse(null);
        return new YggdrasilSession(clientToken, accessToken, new GameProfile(uuid, name), null, new User(userId, userProperties));
    }

    public Map<Object, Object> toStorage() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");
        if (user == null)
            throw new IllegalStateException("No user is specified");

        return mapOf(
                pair("clientToken", clientToken),
                pair("accessToken", accessToken),
                pair("uuid", UUIDTypeAdapter.fromUUID(selectedProfile.getId())),
                pair("displayName", selectedProfile.getName()),
                pair("userid", user.getId()),
                pair("userProperties", user.getProperties()));
    }

    public AuthInfo toAuthInfo() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");
        if (user == null)
            throw new IllegalStateException("No user is specified");

        return new AuthInfo(selectedProfile.getName(), selectedProfile.getId(), accessToken,
                Optional.ofNullable(user.getProperties()).map(GSON_PROPERTIES::toJson).orElse("{}"));
    }

    private static final Gson GSON_PROPERTIES = new GsonBuilder().registerTypeAdapter(PropertyMap.class, PropertyMap.LegacySerializer.INSTANCE).create();
}
