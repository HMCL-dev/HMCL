// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@Immutable
public class YggdrasilSession {

    private final String clientToken;
    private final String accessToken;
    private final GameProfile selectedProfile;
    private final List<GameProfile> availableProfiles;

    @Nullable
    private final Map<String, String> userProperties;

    public YggdrasilSession(String clientToken, String accessToken, GameProfile selectedProfile, List<GameProfile> availableProfiles, Map<String, String> userProperties) {
        this.clientToken = clientToken;
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
        this.availableProfiles = availableProfiles;
        this.userProperties = userProperties;

        if (accessToken != null) Logger.registerAccessToken(accessToken);
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
    public List<GameProfile> getAvailableProfiles() {
        return availableProfiles;
    }

    public Map<String, String> getUserProperties() {
        return userProperties;
    }

    public boolean hasProfileName() {
        return selectedProfile != null && StringUtils.isNotBlank(selectedProfile.getName());
    }

    public static YggdrasilSession fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);

        String profileIDText = JsonUtils.getString(metadata, "profileID");
        if (profileIDText == null) {
            throw new IllegalArgumentException("profileID is missing");
        }
        UUID profileID = UUIDs.parse(profileIDText);
        String profileName = JsonUtils.getString(privateData, "profileName", "");
        String clientToken = requireStorageString(privateData, "clientToken");
        String accessToken = requireStorageString(privateData, "accessToken");
        @Nullable Map<String, String> userProperties = privateData.get("userProperties") instanceof JsonObject userPropertiesObject
                ? GSON_PROPERTIES.fromJson(userPropertiesObject, JsonUtils.mapTypeOf(String.class, String.class))
                : null;
        return new YggdrasilSession(clientToken, accessToken, new GameProfile(profileID, profileName), null, userProperties);
    }

    public void writePrivateData(JsonObject privateData) {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");

        privateData.addProperty("clientToken", clientToken);
        privateData.addProperty("accessToken", accessToken);
        privateData.addProperty("profileName", selectedProfile.getName());
        privateData.add("userProperties", GSON_PROPERTIES.toJsonTree(userProperties));
    }

    private static String requireStorageString(JsonObject storage, String name) {
        String value = JsonUtils.getString(storage, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value;
    }

    public AuthInfo toAuthInfo() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");

        return new AuthInfo(selectedProfile.getName(), selectedProfile.getId(), accessToken, AuthInfo.USER_TYPE_MSA,
                Optional.ofNullable(userProperties)
                        .map(properties -> properties.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        e -> Collections.singleton(e.getValue()))))
                        .map(GSON_PROPERTIES::toJson).orElse("{}"));
    }

    private static final Gson GSON_PROPERTIES = new Gson();
}
