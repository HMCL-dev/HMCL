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
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 * @author Glavo
 * @since <a href="https://manual.littlesk.in/advanced/oauth2/device-authorization-grant#%E5%85%B3%E4%BA%8E-id-%E4%BB%A4%E7%89%8C">About Id Token</a>
 */
@JsonSerializable
public record LittleSkinIdToken(@SerializedName("aud") String audience,
                                @SerializedName("exp") long expirationTime,
                                @SerializedName("iat") long issuedAt,
                                @SerializedName("iss") String issuer,
                                @SerializedName("sub") String subject,
                                @SerializedName("selectedProfile") CompleteGameProfile selectedProfile) implements Validation {

    @Override
    public void validate() throws JsonParseException {
        Validation.requireNonNull(selectedProfile, "selectedProfile");
        selectedProfile.validate();
    }
}
