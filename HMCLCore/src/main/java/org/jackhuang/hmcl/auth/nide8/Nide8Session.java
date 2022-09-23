package org.jackhuang.hmcl.auth.nide8;

import com.google.gson.Gson;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Pair.pair;

public class Nide8Session {
    private final String serverID;
    private final String clientToken;
    private final String accessToken;
    private final Nide8GameProfile selectedProfile;
    private final List<Nide8GameProfile> availableProfiles;
    @Nullable
    private final Map<String, String> userProperties;
    private static Nide8InjectorArtifactProvider downloader;

    public Nide8Session(Nide8InjectorArtifactProvider downloader, String serverID, String clientToken, String accessToken, Nide8GameProfile selectedProfile, List<Nide8GameProfile> availableProfiles, Map<String, String> userProperties) {
        this.serverID = serverID;
        this.clientToken = clientToken;
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
        this.availableProfiles = availableProfiles;
        this.userProperties = userProperties;
        Nide8Session.downloader = downloader;

        if (accessToken != null) Logging.registerAccessToken(accessToken);
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
    public Nide8GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    /**
     * @return nullable (null if the YggdrasilSession is loaded from storage)
     */
    public List<Nide8GameProfile> getAvailableProfiles() {
        return availableProfiles;
    }

    public Map<String, String> getUserProperties() {
        return userProperties;
    }

    public String getServerID() {
        return serverID;
    }

    public static Nide8Session fromStorage(Map<?, ?> storage) {
        Objects.requireNonNull(storage);

        UUID uuid = tryCast(storage.get("uuid"), String.class).map(UUIDTypeAdapter::fromString).orElseThrow(() -> new IllegalArgumentException("uuid is missing"));
        String name = tryCast(storage.get("displayName"), String.class).orElseThrow(() -> new IllegalArgumentException("displayName is missing"));
        String clientToken = tryCast(storage.get("clientToken"), String.class).orElseThrow(() -> new IllegalArgumentException("clientToken is missing"));
        String accessToken = tryCast(storage.get("accessToken"), String.class).orElseThrow(() -> new IllegalArgumentException("accessToken is missing"));
        String serverID = tryCast(storage.get("serverID"), String.class).orElseThrow(() -> new IllegalArgumentException("serverID is missing"));
        Map<String, String> userProperties = tryCast(storage.get("userProperties"), Map.class).orElse(null);
        return new Nide8Session(downloader, serverID, clientToken, accessToken, new Nide8GameProfile(serverID, uuid, name), null, userProperties);
    }

    public Map<Object, Object> toStorage() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");

        return mapOf(
                pair("username", selectedProfile.getName()),
                pair("serverID", serverID),
                pair("clientToken", clientToken),
                pair("accessToken", accessToken),
                pair("uuid", UUIDTypeAdapter.fromUUID(selectedProfile.getId())),
                pair("displayName", selectedProfile.getName()),
                pair("userProperties", userProperties));
    }

    public Nide8AuthInfo toAuthInfo() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");

        try {
            Nide8InjectorArtifactInfo artifact = downloader.getArtifactInfo();

            return new Nide8AuthInfo(artifact, serverID, selectedProfile.getName(), selectedProfile.getId(), accessToken,
                    Optional.ofNullable(userProperties)
                            .map(properties -> properties.entrySet().stream()
                                    .collect(Collectors.toMap(Map.Entry::getKey,
                                            e -> Collections.singleton(e.getValue()))))
                            .map(GSON_PROPERTIES::toJson).orElse("{}"));
        } catch (Exception e) {
            return null;
        }
    }

    private static final Gson GSON_PROPERTIES = new Gson();

}
