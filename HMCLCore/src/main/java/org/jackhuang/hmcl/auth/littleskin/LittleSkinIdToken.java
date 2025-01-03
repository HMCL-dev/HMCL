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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 * @author Glavo
 * @since <a href="https://manual.littlesk.in/advanced/oauth2/device-authorization-grant#%E5%85%B3%E4%BA%8E-id-%E4%BB%A4%E7%89%8C">About Id Token</a>
 */
public final class LittleSkinIdToken implements Validation {
    @SerializedName("aud")
    private final String audience;

    @SerializedName("exp")
    private final long expirationTime;

    @SerializedName("iat")
    private final long issuedAt;

    @SerializedName("iss")
    private final String issuer;

    @SerializedName("sub")
    private final String subject;

    @SerializedName("selectedProfile")
    private final CompleteGameProfile selectedProfile;

    public LittleSkinIdToken(String audience, long expirationTime, long issuedAt, String issuer, String subject, CompleteGameProfile selectedProfile) {
        this.audience = audience;
        this.expirationTime = expirationTime;
        this.issuedAt = issuedAt;
        this.issuer = issuer;
        this.subject = subject;
        this.selectedProfile = selectedProfile;
    }

    public String getAudience() {
        return audience;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public CompleteGameProfile getSelectedProfile() {
        return selectedProfile;
    }

    @Override
    public void validate() throws JsonParseException {
        Validation.requireNonNull(selectedProfile, "selectedProfile");
        selectedProfile.validate();
    }
}
