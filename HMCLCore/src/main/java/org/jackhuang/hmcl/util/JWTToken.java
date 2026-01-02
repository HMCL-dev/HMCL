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
package org.jackhuang.hmcl.util;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.gson.JsonUtils.GSON;

/**
 * @author Glavo
 */
public final class JWTToken<T> {

    public static <T> JWTToken<T> parse(Class<T> type, final String token) {
        return parse(TypeToken.get(type), token);
    }

    public static <T> JWTToken<T> parse(TypeToken<T> type, final String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3)
            throw new IllegalArgumentException("Invalid JWT token: " + token);

        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            Header header = GSON.fromJson(new String(decoder.decode(parts[0]), UTF_8), Header.class);
            T payload = GSON.fromJson(new String(decoder.decode(parts[1]), UTF_8), type);
            String signature = parts[2];
            return new JWTToken<>(header, payload, signature);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Invalid JWT token: " + token, e);
        }
    }

    private final Header header;
    private final T payload;
    private final String signature;

    public JWTToken(Header header, T payload, String signature) {
        this.header = header;
        this.payload = payload;
        this.signature = signature;
    }

    public Header getHeader() {
        return header;
    }

    public T getPayload() {
        return payload;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }

    public static final class Header {
        @SerializedName("alg")
        private final String algorithm;

        @SerializedName("typ")
        private final String type;

        @SerializedName("cty")
        private final String contentType;

        @SerializedName("kid")
        private final String keyId;

        public Header(String algorithm, String type, String contentType, String keyId) {
            this.algorithm = algorithm;
            this.type = type;
            this.contentType = contentType;
            this.keyId = keyId;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getType() {
            return type;
        }

        public String getContentType() {
            return contentType;
        }

        public String getKeyId() {
            return keyId;
        }

        @Override
        public String toString() {
            return "JWTToken.Header " + GSON.toJson(this);
        }
    }
}
