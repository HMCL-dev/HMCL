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

import com.google.gson.JsonObject;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public record OfflineSkinConfig(Type type, TextureModel textureModel, String localSkinPath, String localCapePath) {

    public enum Type {
        DEFAULT,
        ALEX,
        ARI,
        EFE,
        KAI,
        MAKENA,
        NOOR,
        STEVE,
        SUNNY,
        ZURI,
        LOCAL_FILE;

        public static Type fromStorage(String type) {
            return switch (type) {
                case "default" -> DEFAULT;
                case "alex" -> ALEX;
                case "ari" -> ARI;
                case "efe" -> EFE;
                case "kai" -> KAI;
                case "makena" -> MAKENA;
                case "noor" -> NOOR;
                case "steve" -> STEVE;
                case "sunny" -> SUNNY;
                case "zuri" -> ZURI;
                case "local_file" -> LOCAL_FILE;
                default -> null;
            };
        }
    }

    @Override
    public TextureModel textureModel() {
        return textureModel == null ? TextureModel.WIDE : textureModel;
    }

    public Task<LoadedOfflineSkin> load() {
        switch (type) {
            case DEFAULT:
                return Task.supplyAsync(() -> null);
            case ALEX:
            case ARI:
            case EFE:
            case KAI:
            case MAKENA:
            case NOOR:
            case STEVE:
            case SUNNY:
            case ZURI:
                TextureModel model = this.textureModel != null ? this.textureModel : type == Type.ALEX ? TextureModel.SLIM : TextureModel.WIDE;
                String resource = (model == TextureModel.SLIM ? "/assets/img/skin/slim/" : "/assets/img/skin/wide/") + type.name().toLowerCase(Locale.ROOT) + ".png";

                return Task.supplyAsync(() -> new LoadedOfflineSkin(
                        model,
                        HashedTexture.loadTexture(new Image(resource)),
                        null
                ));
            case LOCAL_FILE:
                return Task.supplyAsync(() -> {
                    HashedTexture skin = null, cape = null;
                    Optional<Path> skinPath = FileUtils.tryGetPath(localSkinPath);
                    Optional<Path> capePath = FileUtils.tryGetPath(localCapePath);
                    if (skinPath.isPresent()) skin = HashedTexture.loadTexture(Files.newInputStream(skinPath.get()));
                    if (capePath.isPresent()) cape = HashedTexture.loadTexture(Files.newInputStream(capePath.get()));
                    return new LoadedOfflineSkin(textureModel(), skin, cape);
                });
            default:
                throw new UnsupportedOperationException();
        }
    }

    public void writeStorage(JsonObject storage) {
        storage.addProperty("type", type.name().toLowerCase(Locale.ROOT));
        storage.addProperty("localSkinPath", localSkinPath);
        storage.addProperty("localCapePath", localCapePath);
    }

    public static OfflineSkinConfig fromStorage(JsonObject storage) {
        if (storage == null) return null;

        String typeText = JsonUtils.getString(storage, "type");
        Type type = typeText != null ? Type.fromStorage(typeText) : Type.DEFAULT;
        if (type == null) {
            type = Type.DEFAULT;
        }
        String textureModel = JsonUtils.getString(storage, "textureModel", "default");
        String localSkinPath = JsonUtils.getString(storage, "localSkinPath");
        String localCapePath = JsonUtils.getString(storage, "localCapePath");

        return new OfflineSkinConfig(type, "slim".equals(textureModel) ? TextureModel.SLIM : TextureModel.WIDE, localSkinPath, localCapePath);
    }

}
