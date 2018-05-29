package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class YggdrasilSession {

    private final String accessToken;
    private GameProfile selectedProfile;
    private final GameProfile[] availableProfiles;
    private final User user;

    public YggdrasilSession(String accessToken, GameProfile selectedProfile, GameProfile[] availableProfiles, User user) {
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
        this.availableProfiles = availableProfiles;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    public GameProfile[] getAvailableProfiles() {
        return availableProfiles;
    }

    public User getUser() {
        return user;
    }

    public void setAvailableProfile(GameProfile profile) {
        if (availableProfiles != null)
            for (int i = 0; i < availableProfiles.length; ++i)
                if (availableProfiles[i].getId().equals(profile.getId()))
                    availableProfiles[i] = profile;

        if (selectedProfile != null && profile.getId().equals(selectedProfile.getId()))
            selectedProfile = profile;
    }

    public void setSelectedProfile(GameProfile selectedProfile) {
        this.selectedProfile = selectedProfile;

        setAvailableProfile(selectedProfile);
    }

    public static YggdrasilSession fromStorage(Map<?, ?> storage) {
        Optional<String> profileId = tryCast(storage.get("uuid"), String.class);
        Optional<String> profileName = tryCast(storage.get("displayName"), String.class);
        GameProfile profile = null;
        if (profileId.isPresent() && profileName.isPresent()) {
            profile = new GameProfile(UUIDTypeAdapter.fromString(profileId.get()), profileName.get(),
                    tryCast(storage.get("profileProperties"), Map.class).map(PropertyMap::fromMap).orElseGet(PropertyMap::new));
        }

        return new YggdrasilSession(
                tryCast(storage.get("accessToken"), String.class).orElse(null),
                profile,
                null,
                tryCast(storage.get("userid"), String.class)
                        .map(userId -> new User(userId, tryCast(storage.get("userProperties"), Map.class).map(PropertyMap::fromMap).orElse(null)))
                        .orElse(null)
        );
    }

    public Map<Object, Object> toStorage() {
        Map<Object, Object> storage = new HashMap<>();
        storage.put("accessToken", accessToken);
        if (selectedProfile != null) {
            storage.put("uuid", UUIDTypeAdapter.fromUUID(selectedProfile.getId()));
            storage.put("displayName", selectedProfile.getName());
            storage.put("profileProperties", selectedProfile.getProperties());
        }
        if (user != null) {
            storage.put("userid", user.getId());
            storage.put("userProperties", user.getProperties());
        }
        return storage;
    }
}
