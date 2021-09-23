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
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class Skin {

    public enum Type {
        DEFAULT,
        STEVE,
        ALEX,
        LOCAL_FILE,
        CUSTOM_SKIN_LOADER_API,
        YGGDRASIL_API
    }

    private Type type;
    private String value;

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Task<Texture> toTexture(String username) {
        switch (type) {
            case DEFAULT:
                return Task.supplyAsync(() -> null);
            case STEVE:
                return Task.supplyAsync(() -> Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/steve.png")));
            case ALEX:
                return Task.supplyAsync(() -> Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/alex.png")));
            case LOCAL_FILE:
                return Task.supplyAsync(() -> Texture.loadTexture(Files.newInputStream(Paths.get(value))));
            case CUSTOM_SKIN_LOADER_API:
                return Task.composeAsync(() -> new GetTask(new URL(String.format("%s/%s.json", value, username))))
                        .thenComposeAsync(json -> {
                            SkinJson result = JsonUtils.GSON.fromJson(json, SkinJson.class);

                            if (!result.hasSkin()) {
                                return Task.supplyAsync(() -> null);
                            }

                            return new FetchBytesTask(new URL(String.format("%s/textures/%s", value, result.getHash())), 3);
                        }).thenApplyAsync(Texture::loadTexture);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static class FetchBytesTask extends FetchTask<InputStream> {

        public FetchBytesTask(URL url, int retry) {
            super(Collections.singletonList(url), retry);
        }

        @Override
        protected void useCachedResult(Path cachedFile) throws IOException {
            setResult(Files.newInputStream(cachedFile));
        }

        @Override
        protected EnumCheckETag shouldCheckETag() {
            return EnumCheckETag.CHECK_E_TAG;
        }

        @Override
        protected Context getContext(URLConnection conn, boolean checkETag) throws IOException {
            return new Context() {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                @Override
                public void write(byte[] buffer, int offset, int len) {
                    baos.write(buffer, offset, len);
                }

                @Override
                public void close() throws IOException {
                    if (!isSuccess()) return;

                    setResult(new ByteArrayInputStream(baos.toByteArray()));

                    if (checkETag) {
                        repository.cacheBytes(baos.toByteArray(), conn);
                    }
                }
            };
        }
    }

    private static class SkinJson {
        private final String username;
        private final String skin;
        private final String cape;
        private final String elytra;

        @SerializedName(value = "textures", alternate = { "skins" })
        private final TextureJson textures;

        public SkinJson(String username, String skin, String cape, String elytra, TextureJson textures) {
            this.username = username;
            this.skin = skin;
            this.cape = cape;
            this.elytra = elytra;
            this.textures = textures;
        }

        public boolean hasSkin() {
            return StringUtils.isNotBlank(username);
        }

        @Nullable
        public TextureModel getModel() {
            if (textures != null && textures.slim != null) {
                return TextureModel.ALEX;
            } else if (textures != null && textures.defaultSkin != null) {
                return TextureModel.STEVE;
            } else {
                return null;
            }
        }

        public String getAlexModelHash() {
            if (textures != null && textures.slim != null) {
                return textures.slim;
            } else {
                return null;
            }
        }

        public String getSteveModelHash() {
            if (textures != null && textures.defaultSkin != null) {
                return textures.defaultSkin;
            } else return skin;
        }

        public String getHash() {
            TextureModel model = getModel();
            if (model == TextureModel.ALEX)
                return getAlexModelHash();
            else if (model == TextureModel.STEVE)
                return getSteveModelHash();
            else
                return null;
        }

        public static class TextureJson {
            @SerializedName("default")
            private final String defaultSkin;

            private final String slim;
            private final String cape;
            private final String elytra;

            public TextureJson(String defaultSkin, String slim, String cape, String elytra) {
                this.defaultSkin = defaultSkin;
                this.slim = slim;
                this.cape = cape;
                this.elytra = elytra;
            }
        }
    }
}
