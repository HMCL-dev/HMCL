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

import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
        private final Image image;
        private final Map<String, String> metadata;

        public LoadedTexture(Image image, Map<String, String> metadata) {
            this.image = requireNonNull(image);
            this.metadata = requireNonNull(metadata);
        }

        public Image getImage() {
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
        String prefix = hash.length() > 2 ? hash.substring(0, 2) : "xx" ;
        return TEXTURES_DIR.resolve(prefix).resolve(hash);
    }

    public static LoadedTexture loadTexture(Texture texture) throws Throwable {
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

        Image img;
        try (InputStream in = Files.newInputStream(file)) {
            img = new Image(in);
        }

        if (img.isError())
            throw img.getException();

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
        Image skin;
        try {
            skin = new Image(path);
            if (skin.isError())
                throw skin.getException();
        } catch (Throwable e) {
            throw new ResourceNotFoundError("Cannot load default skin from " + path, e);
        }

        DEFAULT_SKINS.put(model, new LoadedTexture(skin, singletonMap("model", model.modelName)));
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
                            } catch (Throwable e) {
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
                            } catch (Throwable e) {
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
    public static void drawAvatar(Canvas canvas, Image skin) {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int size = (int) canvas.getWidth();
        int scale = (int) skin.getWidth() / 64;
        int faceOffset = (int) Math.round(size / 18.0);

        GraphicsContext g = canvas.getGraphicsContext2D();
        try {
            g.setImageSmoothing(false);
            drawAvatarFX(g, skin, size, scale, faceOffset);
        } catch (NoSuchMethodError ignored) {
            // Earlier JavaFX did not support GraphicsContext::setImageSmoothing, fallback to Java 2D
            drawAvatarJ2D(g, skin, size, scale, faceOffset);
        }
    }

    private static void drawAvatarFX(GraphicsContext g, Image skin, int size, int scale, int faceOffset) {
        g.drawImage(skin,
                8 * scale, 8 * scale, 8 * scale, 8 * scale,
                faceOffset, faceOffset, size - 2 * faceOffset, size - 2 * faceOffset);
        g.drawImage(skin,
                40 * scale, 8 * scale, 8 * scale, 8 * scale,
                0, 0, size, size);
    }

    private static void drawAvatarJ2D(GraphicsContext g, Image skin, int size, int scale, int faceOffset) {
        BufferedImage bi = FXUtils.fromFXImage(skin);

        BufferedImage avatar = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = avatar.createGraphics();

        g2d.drawImage(bi,
                faceOffset, faceOffset, size - faceOffset, size - faceOffset,
                8 * scale, 8 * scale, 16 * scale, 16 * scale,
                null);
        g2d.drawImage(bi,
                0, 0, size, size,
                40 * scale, 8 * scale, 48 * scale, 16 * scale, null);

        g2d.dispose();

        PixelWriter pw = g.getPixelWriter();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                pw.setArgb(x, y, avatar.getRGB(x, y));
            }
        }
    }

    private static final class SkinBindingChangeListener implements ChangeListener<LoadedTexture> {
        static final WeakHashMap<Canvas, SkinBindingChangeListener> hole = new WeakHashMap<>();

        final WeakReference<Canvas> canvasRef;
        final ObjectBinding<LoadedTexture> binding;

        SkinBindingChangeListener(Canvas canvas, ObjectBinding<LoadedTexture> binding) {
            this.canvasRef = new WeakReference<>(canvas);
            this.binding = binding;
        }

        @Override
        public void changed(ObservableValue<? extends LoadedTexture> observable,
                            LoadedTexture oldValue, LoadedTexture loadedTexture) {
            Canvas canvas = canvasRef.get();
            if (canvas != null)
                drawAvatar(canvas, loadedTexture.image);
        }
    }

    public static void fxAvatarBinding(Canvas canvas, ObjectBinding<LoadedTexture> skinBinding) {
        synchronized (SkinBindingChangeListener.hole) {
            SkinBindingChangeListener oldListener = SkinBindingChangeListener.hole.remove(canvas);
            if (oldListener != null)
                oldListener.binding.removeListener(oldListener);

            SkinBindingChangeListener listener = new SkinBindingChangeListener(canvas, skinBinding);
            listener.changed(skinBinding, null, skinBinding.get());
            skinBinding.addListener(listener);

            SkinBindingChangeListener.hole.put(canvas, listener);
        }
    }

    public static void bindAvatar(Canvas canvas, YggdrasilService service, UUID uuid) {
        fxAvatarBinding(canvas, skinBinding(service, uuid));
    }

    public static void bindAvatar(Canvas canvas, Account account) {
        if (account instanceof YggdrasilAccount || account instanceof MicrosoftAccount)
            fxAvatarBinding(canvas, skinBinding(account));
        else {
            unbindAvatar(canvas);
            drawAvatar(canvas, getDefaultSkin(TextureModel.detectUUID(account.getUUID())).image);
        }
    }

    public static void unbindAvatar(Canvas canvas) {
        synchronized (SkinBindingChangeListener.hole) {
            SkinBindingChangeListener oldListener = SkinBindingChangeListener.hole.remove(canvas);
            if (oldListener != null)
                oldListener.binding.removeListener(oldListener);
        }
    }
    // ====
}
