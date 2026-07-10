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

/// @param selectedProfile nullable (null if no character is selected)
/// @param availableProfiles nullable (null if the YggdrasilSession is loaded from storage)
@Immutable
public record YggdrasilSession(String clientToken, String accessToken, @Nullable GameProfile selectedProfile,
                               List<GameProfile> availableProfiles, @Nullable Map<String, String> userProperties) {

    public YggdrasilSession {
        if (accessToken != null) Logger.registerAccessToken(accessToken);
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
