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
package org.jackhuang.hmcl.game;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.ResourceNotFoundError;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.Logging.LOG;

/**
 * @author yushijinhun
 */
public final class TexturesLoader {

    private TexturesLoader() {
    }

    // ==== Texture Loading ====
    public static class LoadedTexture {
        private final BufferedImage image;
        private final Map<String, String> metadata;

        public LoadedTexture(BufferedImage image, Map<String, String> metadata) {
            this.image = requireNonNull(image);
            this.metadata = requireNonNull(metadata);
        }

        public BufferedImage getImage() {
            return image;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }
    }

    private static final ThreadPoolExecutor POOL = threadPool("TexturesDownload", true, 2, 10, TimeUnit.SECONDS);
    private static final Path TEXTURES_DIR = Metadata.MINECRAFT_DIRECTORY.resolve("assets").resolve("skins");

    private static Path getTexturePath(Texture texture) {
        String url = texture.getUrl();
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot < slash) {
            dot = url.length();
        }
        String hash = url.substring(slash + 1, dot);
        String prefix = hash.length() > 2 ? hash.substring(0, 2) : "xx";
        return TEXTURES_DIR.resolve(prefix).resolve(hash);
    }

    public static LoadedTexture loadTexture(Texture texture) throws IOException {
        if (StringUtils.isBlank(texture.getUrl())) {
            throw new IOException("Texture url is empty");
        }

        Path file = getTexturePath(texture);
        if (!Files.isRegularFile(file)) {
            // download it
            try {
                new FileDownloadTask(new URL(texture.getUrl()), file.toFile()).run();
                LOG.info("Texture downloaded: " + texture.getUrl());
            } catch (Exception e) {
                if (Files.isRegularFile(file)) {
                    // concurrency conflict?
                    LOG.log(Level.WARNING, "Failed to download texture " + texture.getUrl() + ", but the file is available", e);
                } else {
                    throw new IOException("Failed to download texture " + texture.getUrl());
                }
            }
        }

        BufferedImage img;
        try (InputStream in = Files.newInputStream(file)) {
            img = ImageIO.read(in);
        }
        if (img == null)
            throw new IOException("Texture is malformed");

        Map<String, String> metadata = texture.getMetadata();
        if (metadata == null) {
            metadata = emptyMap();
        }
        return new LoadedTexture(img, metadata);
    }
    // ====

    // ==== Skins ====
    private final static Map<TextureModel, LoadedTexture> DEFAULT_SKINS = new EnumMap<>(TextureModel.class);

    static {
        loadDefaultSkin("/assets/img/skin/steve.png", TextureModel.STEVE);
        loadDefaultSkin("/assets/img/skin/alex.png", TextureModel.ALEX);
    }

    private static void loadDefaultSkin(String path, TextureModel model) {
        try (InputStream in = ResourceNotFoundError.getResourceAsStream(path)) {
            DEFAULT_SKINS.put(model, new LoadedTexture(ImageIO.read(in), singletonMap("model", model.modelName)));
        } catch (Throwable e) {
            throw new ResourceNotFoundError("Cannoot load default skin from " + path, e);
        }
    }

    public static LoadedTexture getDefaultSkin(TextureModel model) {
        return DEFAULT_SKINS.get(model);
    }

    public static ObjectBinding<LoadedTexture> skinBinding(YggdrasilService service, UUID uuid) {
        LoadedTexture uuidFallback = getDefaultSkin(TextureModel.detectUUID(uuid));
        return BindingMapping.of(service.getProfileRepository().binding(uuid))
                .map(profile -> profile
                        .flatMap(it -> {
                            try {
                                return YggdrasilService.getTextures(it);
                            } catch (ServerResponseMalformedException e) {
                                LOG.log(Level.WARNING, "Failed to parse texture payload", e);
                                return Optional.empty();
                            }
                        })
                        .flatMap(it -> Optional.ofNullable(it.get(TextureType.SKIN)))
                        .filter(it -> StringUtils.isNotBlank(it.getUrl())))
                .asyncMap(it -> {
                    if (it.isPresent()) {
                        Texture texture = it.get();
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                return loadTexture(texture);
                            } catch (IOException e) {
                                LOG.log(Level.WARNING, "Failed to load texture " + texture.getUrl() + ", using fallback texture", e);
                                return uuidFallback;
                            }
                        }, POOL);
                    } else {
                        return CompletableFuture.completedFuture(uuidFallback);
                    }
                }, uuidFallback);
    }

    public static ObjectBinding<LoadedTexture> skinBinding(Account account) {
        LoadedTexture uuidFallback = getDefaultSkin(TextureModel.detectUUID(account.getUUID()));
        return BindingMapping.of(account.getTextures())
                .map(textures -> textures
                        .flatMap(it -> Optional.ofNullable(it.get(TextureType.SKIN)))
                        .filter(it -> StringUtils.isNotBlank(it.getUrl())))
                .asyncMap(it -> {
                    if (it.isPresent()) {
                        Texture texture = it.get();
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                return loadTexture(texture);
                            } catch (IOException e) {
                                LOG.log(Level.WARNING, "Failed to load texture " + texture.getUrl() + ", using fallback texture", e);
                                return uuidFallback;
                            }
                        }, POOL);
                    } else {
                        return CompletableFuture.completedFuture(uuidFallback);
                    }
                }, uuidFallback);
    }

    // ====

    // ==== Avatar ====
    public static BufferedImage toAvatar(BufferedImage skin, int size) {
        BufferedImage avatar = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = avatar.createGraphics();

        int scale = skin.getWidth() / 64;
        int faceOffset = (int) Math.round(size / 18.0);
        g.drawImage(skin,
                faceOffset, faceOffset, size - faceOffset, size - faceOffset,
                8 * scale, 8 * scale, 16 * scale, 16 * scale,
                null);
        g.drawImage(skin,
                0, 0, size, size,
                40 * scale, 8 * scale, 48 * scale, 16 * scale, null);

        g.dispose();
        return avatar;
    }

    public static ObjectBinding<Image> fxAvatarBinding(YggdrasilService service, UUID uuid, int size) {
        return BindingMapping.of(skinBinding(service, uuid))
                .map(it -> toAvatar(it.image, size))
                .map(FXUtils::toFXImage);
    }

    public static ObjectBinding<Image> fxAvatarBinding(Account account, int size) {
        if (account instanceof YggdrasilAccount || account instanceof MicrosoftAccount) {
            return BindingMapping.of(skinBinding(account))
                    .map(it -> toAvatar(it.image, size))
                    .map(FXUtils::toFXImage);
        } else {
            return Bindings.createObjectBinding(
                    () -> FXUtils.toFXImage(toAvatar(getDefaultSkin(TextureModel.detectUUID(account.getUUID())).image, size)));
        }
    }
    // ====
}
