/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import static java.util.Collections.singletonMap;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.Logging.LOG;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.javafx.MultiStepBinding;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

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
            this.image = image;
            this.metadata = metadata;
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
        return new LoadedTexture(img, texture.getMetadata());
    }
    // ====

    // ==== Skins ====
    private final static Map<TextureModel, LoadedTexture> DEFAULT_SKINS = new EnumMap<>(TextureModel.class);
    static {
        loadDefaultSkin("/assets/img/steve.png", TextureModel.STEVE);
        loadDefaultSkin("/assets/img/alex.png", TextureModel.ALEX);
    }
    private static void loadDefaultSkin(String path, TextureModel model) {
        try (InputStream in = TexturesLoader.class.getResourceAsStream(path)) {
            DEFAULT_SKINS.put(model, new LoadedTexture(ImageIO.read(in), singletonMap("model", model.modelName)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LoadedTexture getDefaultSkin(TextureModel model) {
        return DEFAULT_SKINS.get(model);
    }

    public static ObjectBinding<LoadedTexture> skinBinding(YggdrasilService service, UUID uuid) {
        LoadedTexture uuidFallback = getDefaultSkin(TextureModel.detectUUID(uuid));
        return MultiStepBinding.of(service.getProfileRepository().binding(uuid))
                .map(profile -> profile
                        .flatMap(it -> {
                            try {
                                return YggdrasilService.getTextures(it);
                            } catch (ServerResponseMalformedException e) {
                                LOG.log(Level.WARNING, "Failed to parse texture payload", e);
                                return Optional.empty();
                            }
                        })
                        .flatMap(it -> Optional.ofNullable(it.get(TextureType.SKIN))))
                .asyncMap(it -> {
                    if (it.isPresent()) {
                        Texture texture = it.get();
                        try {
                            return loadTexture(texture);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to load texture " + texture.getUrl() + ", using fallback texture", e);
                            return getDefaultSkin(TextureModel.detectModelName(texture.getMetadata()));
                        }
                    } else {
                        return uuidFallback;
                    }
                }, uuidFallback, POOL);
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
        return MultiStepBinding.of(skinBinding(service, uuid))
                .map(it -> toAvatar(it.image, size))
                .map(img -> SwingFXUtils.toFXImage(img, null));
    }

    public static ObjectBinding<Image> fxAvatarBinding(Account account, int size) {
        if (account instanceof YggdrasilAccount) {
            return fxAvatarBinding(((YggdrasilAccount) account).getYggdrasilService(), account.getUUID(), size);
        } else {
            return Bindings.createObjectBinding(
                    () -> SwingFXUtils.toFXImage(toAvatar(getDefaultSkin(TextureModel.detectUUID(account.getUUID())).image, size), null));
        }
    }
    // ====
}
