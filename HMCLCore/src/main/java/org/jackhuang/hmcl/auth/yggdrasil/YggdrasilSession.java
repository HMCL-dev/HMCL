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
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Pair.pair;

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

    public static YggdrasilSession fromStorage(Map<?, ?> storage) {
        Objects.requireNonNull(storage);

        UUID uuid = tryCast(storage.get("uuid"), String.class).map(UUIDTypeAdapter::fromString).orElseThrow(() -> new IllegalArgumentException("uuid is missing"));
        String name = tryCast(storage.get("displayName"), String.class).orElseThrow(() -> new IllegalArgumentException("displayName is missing"));
        String clientToken = tryCast(storage.get("clientToken"), String.class).orElseThrow(() -> new IllegalArgumentException("clientToken is missing"));
        String accessToken = tryCast(storage.get("accessToken"), String.class).orElseThrow(() -> new IllegalArgumentException("accessToken is missing"));
        @SuppressWarnings("unchecked")
        Map<String, String> userProperties = tryCast(storage.get("userProperties"), Map.class).orElse(null);
        return new YggdrasilSession(clientToken, accessToken, new GameProfile(uuid, name), null, userProperties);
    }

    public Map<Object, Object> toStorage() {
        if (selectedProfile == null)
            throw new IllegalStateException("No character is selected");

        return mapOf(
                pair("clientToken", clientToken),
                pair("accessToken", accessToken),
                pair("uuid", UUIDTypeAdapter.fromUUID(selectedProfile.getId())),
                pair("displayName", selectedProfile.getName()),
                pair("userProperties", userProperties));
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
