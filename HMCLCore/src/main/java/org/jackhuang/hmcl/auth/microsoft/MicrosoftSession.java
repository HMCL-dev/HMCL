/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.logging.Logger;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class MicrosoftSession {
    private final String tokenType;
    private final long notAfter;
    private final String accessToken;
    private final String refreshToken;
    private final User user;
    private final GameProfile profile;

    public MicrosoftSession(String tokenType, String accessToken, long notAfter, String refreshToken, User user, GameProfile profile) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
        this.notAfter = notAfter;
        this.refreshToken = refreshToken;
        this.user = user;
        this.profile = profile;

        if (accessToken != null) Logger.registerAccessToken(accessToken);
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getNotAfter() {
        return notAfter;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAuthorization() {
        return String.format("%s %s", getTokenType(), getAccessToken());
    }

    public User getUser() {
        return user;
    }

    public GameProfile getProfile() {
        return profile;
    }

    /// Returns whether the stored session contains a usable Minecraft profile name.
    public boolean hasProfileName() {
        return profile != null && StringUtils.isNotBlank(profile.getName());
    }

    /// Loads a Microsoft session from persisted account metadata and private data.
    public static MicrosoftSession fromStorage(JsonObject metadata, JsonObject privateData) {
        String profileIDText = JsonUtils.getString(metadata, "profileID");
        if (profileIDText == null) {
            throw new IllegalArgumentException("profileID is missing");
        }
        UUID profileID = UUIDTypeAdapter.fromString(profileIDText);
        String profileName = JsonUtils.getString(privateData, "profileName", "");
        String tokenType = requireStorageString(privateData, "tokenType");
        String accessToken = requireStorageString(privateData, "accessToken");
        String refreshToken = requireStorageString(privateData, "refreshToken");
        JsonElement notAfterElement = privateData.get("notAfter");
        long notAfter = notAfterElement != null
                && notAfterElement.isJsonPrimitive()
                && notAfterElement.getAsJsonPrimitive().isNumber()
                ? notAfterElement.getAsLong()
                : 0L;
        String userId = requireStorageString(privateData, "userid");
        return new MicrosoftSession(tokenType, accessToken, notAfter, refreshToken, new User(userId), new GameProfile(profileID, profileName));
    }

    /// Writes this session to persisted private account data.
    public void writePrivateData(JsonObject privateData) {
        requireNonNull(profile);
        requireNonNull(user);

        privateData.addProperty("profileName", profile.getName());
        privateData.addProperty("tokenType", tokenType);
        privateData.addProperty("accessToken", accessToken);
        privateData.addProperty("refreshToken", refreshToken);
        privateData.addProperty("notAfter", notAfter);
        privateData.addProperty("userid", user.id);
    }

    /// Reads a required string member from account storage.
    private static String requireStorageString(JsonObject storage, String name) {
        String value = JsonUtils.getString(storage, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return value;
    }

    public AuthInfo toAuthInfo() {
        requireNonNull(profile);

        return new AuthInfo(profile.getName(), profile.getId(), accessToken, AuthInfo.USER_TYPE_MSA, "{}");
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
