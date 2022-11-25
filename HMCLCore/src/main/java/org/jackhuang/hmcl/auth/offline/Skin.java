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
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Pair.pair;

public class Skin {

    public enum Type {
        DEFAULT,
        ALEX,
        ALEXNEW,
        ARI,
        EFE,
        KAI,
        MAKENA,
        NOOR,
        STEVE,
        STEVENEW,
        SUNNY,
        ZURI,
        LOCAL_FILE,
        LITTLE_SKIN,
        CUSTOM_SKIN_LOADER_API,
        YGGDRASIL_API;

        public static Type fromStorage(String type) {
            switch (type) {
                case "default":
                    return DEFAULT;
                case "alex":
                    return ALEX;
                case "alex-new":
                    return ALEXNEW;
                case "ari":
                    return ARI;
                case "efe":
                    return EFE;
                case "kai":
                    return KAI;
                case "makena":
                    return MAKENA;
                case "noor":
                    return NOOR;
                case "steve":
                    return STEVE;
                case "steve-new":
                    return STEVENEW;
                case "sunny":
                    return SUNNY;
                case "zuri":
                    return ZURI;
                case "local_file":
                    return LOCAL_FILE;
                case "little_skin":
                    return LITTLE_SKIN;
                case "custom_skin_loader_api":
                    return CUSTOM_SKIN_LOADER_API;
                case "yggdrasil_api":
                    return YGGDRASIL_API;
                default:
                    return null;
            }
        }
    }

    private final Type type;
    private final String cslApi;
    private final TextureModel textureModel;
    private final String localSkinPath;
    private final String localCapePath;

    public Skin(Type type, String cslApi, TextureModel textureModel, String localSkinPath, String localCapePath) {
        this.type = type;
        this.cslApi = cslApi;
        this.textureModel = textureModel;
        this.localSkinPath = localSkinPath;
        this.localCapePath = localCapePath;
    }

    public Type getType() {
        return type;
    }

    public String getCslApi() {
        return cslApi;
    }

    public TextureModel getTextureModel() {
        return textureModel == null ? TextureModel.STEVE : textureModel;
    }

    public String getLocalSkinPath() {
        return localSkinPath;
    }

    public String getLocalCapePath() {
        return localCapePath;
    }

    public Task<LoadedSkin> load(String username) {
        switch (type) {
            case DEFAULT:
                return Task.supplyAsync(() -> null);
            case ALEX:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.ALEX, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/alex.png")), null));
            case ALEXNEW:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.ALEX, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/alex-new.png")), null));
            case ARI:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/ari.png")), null));
            case EFE:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.ALEX, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/efe.png")), null));
            case KAI:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/kai.png")), null));
            case MAKENA:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.ALEX, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/makena.png")), null));
            case NOOR:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.ALEX, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/noor.png")), null));
            case STEVE:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/steve.png")), null));
            case STEVENEW:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/steve-new.png")), null));
            case SUNNY:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/sunny.png")), null));
            case ZURI:
                return Task.supplyAsync(() -> new LoadedSkin(TextureModel.STEVE, Texture.loadTexture(Skin.class.getResourceAsStream("/assets/img/skin/zuri.png")), null));
            case LOCAL_FILE:
                return Task.supplyAsync(() -> {
                    Texture skin = null, cape = null;
                    Optional<Path> skinPath = FileUtils.tryGetPath(localSkinPath);
                    Optional<Path> capePath = FileUtils.tryGetPath(localCapePath);
                    if (skinPath.isPresent()) skin = Texture.loadTexture(Files.newInputStream(skinPath.get()));
                    if (capePath.isPresent()) cape = Texture.loadTexture(Files.newInputStream(capePath.get()));
                    return new LoadedSkin(getTextureModel(), skin, cape);
                });
            case LITTLE_SKIN:
            case CUSTOM_SKIN_LOADER_API:
                String realCslApi = type == Type.LITTLE_SKIN ? "https://littleskin.cn" : StringUtils.removeSuffix(cslApi, "/");
                return Task.composeAsync(() -> new GetTask(new URL(String.format("%s/%s.json", realCslApi, username))))
                        .thenComposeAsync(json -> {
                            SkinJson result = JsonUtils.GSON.fromJson(json, SkinJson.class);

                            if (!result.hasSkin()) {
                                return Task.supplyAsync(() -> null);
                            }

                            return Task.allOf(
                                    Task.supplyAsync(result::getModel),
                                    result.getHash() == null ? Task.supplyAsync(() -> null) : new FetchBytesTask(new URL(String.format("%s/textures/%s", realCslApi, result.getHash())), 3),
                                    result.getCapeHash() == null ? Task.supplyAsync(() -> null) : new FetchBytesTask(new URL(String.format("%s/textures/%s", realCslApi, result.getCapeHash())), 3)
                            );
                        }).thenApplyAsync(result -> {
                            if (result == null) {
                                return null;
                            }

                            Texture skin, cape;
                            if (result.get(1) != null) {
                                skin = Texture.loadTexture((InputStream) result.get(1));
                            } else {
                                skin = null;
                            }

                            if (result.get(2) != null) {
                                cape = Texture.loadTexture((InputStream) result.get(2));
                            } else {
                                cape = null;
                            }

                            return new LoadedSkin((TextureModel) result.get(0), skin, cape);
                        });
            default:
                throw new UnsupportedOperationException();
        }
    }

    public Map<?, ?> toStorage() {
        return mapOf(
                pair("type", type.name().toLowerCase(Locale.ROOT)),
                pair("cslApi", cslApi),
                pair("textureModel", getTextureModel().modelName),
                pair("localSkinPath", localSkinPath),
                pair("localCapePath", localCapePath)
        );
    }

    public static Skin fromStorage(Map<?, ?> storage) {
        if (storage == null) return null;

        Type type = tryCast(storage.get("type"), String.class).flatMap(t -> Optional.ofNullable(Type.fromStorage(t)))
                .orElse(Type.DEFAULT);
        String cslApi = tryCast(storage.get("cslApi"), String.class).orElse(null);
        String textureModel = tryCast(storage.get("textureModel"), String.class).orElse("default");
        String localSkinPath = tryCast(storage.get("localSkinPath"), String.class).orElse(null);
        String localCapePath = tryCast(storage.get("localCapePath"), String.class).orElse(null);

        TextureModel model;
        if ("default".equals(textureModel)) {
            model = TextureModel.STEVE;
        } else if ("slim".equals(textureModel)) {
            model = TextureModel.ALEX;
        } else {
            model = TextureModel.STEVE;
        }

        return new Skin(type, cslApi, model, localSkinPath, localCapePath);
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

    public static class LoadedSkin {
        private final TextureModel model;
        private final Texture skin;
        private final Texture cape;

        public LoadedSkin(TextureModel model, Texture skin, Texture cape) {
            this.model = model;
            this.skin = skin;
            this.cape = cape;
        }

        public TextureModel getModel() {
            return model;
        }

        public Texture getSkin() {
            return skin;
        }

        public Texture getCape() {
            return cape;
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

        public String getCapeHash() {
            if (textures != null && textures.cape != null) {
                return textures.cape;
            } else return cape;
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
