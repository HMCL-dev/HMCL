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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineSkinConfig;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.game.skin.TextureType;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author yushijinhun
 */
public final class TexturesLoader {

    private TexturesLoader() {
    }

    // ==== Texture Loading ====
    public record LoadedTexture(Image image, Map<String, String> metadata) {
        public LoadedTexture(Image image, Map<String, String> metadata) {
            this.image = requireNonNull(image);
            this.metadata = requireNonNull(metadata);
        }
    }

    private static final ThreadPoolExecutor POOL = threadPool("TexturesDownload", true, 2, 10, TimeUnit.SECONDS);
    private static final Path TEXTURES_DIR = Metadata.HMCL_USER_HOME.resolve("skins");

    private static Path getTexturePath(Texture texture) {
        String url = texture.url();
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot < slash) {
            dot = url.length();
        }
        String hash = url.substring(slash + 1, dot);
        String prefix = hash.length() > 2 ? hash.substring(0, 2) : "xx";
        return TEXTURES_DIR.resolve(prefix).resolve(hash);
    }

    public static LoadedTexture loadTexture(Texture texture) throws Throwable {
        if (StringUtils.isBlank(texture.url())) {
            throw new IOException("Texture url is empty");
        }

        Path file = getTexturePath(texture);
        if (!Files.isRegularFile(file)) {
            // download it
            try {
                new FileDownloadTask(texture.url(), file).run();
                LOG.info("Texture downloaded: " + texture.url());
            } catch (Exception e) {
                if (Files.isRegularFile(file)) {
                    // concurrency conflict?
                    LOG.warning("Failed to download texture " + texture.url() + ", but the file is available", e);
                } else {
                    throw new IOException("Failed to download texture " + texture.url());
                }
            }
        }

        Image img;
        try (InputStream in = Files.newInputStream(file)) {
            img = new Image(in);
        }

        if (img.isError())
            throw img.getException();

        Map<String, String> metadata = texture.metadata();
        if (metadata == null) {
            metadata = emptyMap();
        }
        return new LoadedTexture(img, metadata);
    }
    // ====

    // ==== Skins ====
    private static final String[] DEFAULT_SKINS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};

    public static Image getDefaultSkinImage() {
        return FXUtils.newBuiltinImage("/assets/img/skin/wide/steve.png");
    }

    public static LoadedTexture getDefaultSkin(UUID uuid) {
        int idx = Math.floorMod(uuid.hashCode(), DEFAULT_SKINS.length * 2);
        TextureModel model;
        Image skin;
        if (idx < DEFAULT_SKINS.length) {
            model = TextureModel.SLIM;
            skin = FXUtils.newBuiltinImage("/assets/img/skin/slim/" + DEFAULT_SKINS[idx] + ".png");
        } else {
            model = TextureModel.WIDE;
            skin = FXUtils.newBuiltinImage("/assets/img/skin/wide/" + DEFAULT_SKINS[idx - DEFAULT_SKINS.length] + ".png");
        }

        return new LoadedTexture(skin, singletonMap("model", model.modelName));
    }

    public static TextureModel getDefaultModel(UUID uuid) {
        return TextureModel.WIDE.modelName.equals(getDefaultSkin(uuid).metadata().get("model"))
                ? TextureModel.WIDE
                : TextureModel.SLIM;
    }

    public static ObjectBinding<LoadedTexture> skinBinding(YggdrasilService service, UUID uuid) {
        LoadedTexture uuidFallback = getDefaultSkin(uuid);
        return BindingMapping.of(service.getProfileRepository().binding(uuid))
                .map(profile -> profile
                        .flatMap(it -> {
                            try {
                                return YggdrasilService.getTextures(it);
                            } catch (ServerResponseMalformedException e) {
                                LOG.warning("Failed to parse texture payload", e);
                                return Optional.empty();
                            }
                        })
                        .flatMap(it -> Optional.ofNullable(it.get(TextureType.SKIN)))
                        .filter(it -> StringUtils.isNotBlank(it.url())))
                .asyncMap(it -> {
                    if (it.isPresent()) {
                        Texture texture = it.get();
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                return loadTexture(texture);
                            } catch (Throwable e) {
                                LOG.warning("Failed to load texture " + texture.url() + ", using fallback texture", e);
                                return uuidFallback;
                            }
                        }, POOL);
                    } else {
                        return CompletableFuture.completedFuture(uuidFallback);
                    }
                }, uuidFallback);
    }

    public static ObservableValue<LoadedTexture> skinBinding(Account account) {
        LoadedTexture uuidFallback = getDefaultSkin(account.getProfileID());
        if (account instanceof OfflineAccount offlineAccount) {

            SimpleObjectProperty<LoadedTexture> binding = new SimpleObjectProperty<>();
            InvalidationListener listener = o -> {
                OfflineSkinConfig skin = offlineAccount.getSkin();
                String profileName = offlineAccount.getProfileName();

                binding.set(uuidFallback);
                if (skin != null) {
                    skin.load().setExecutor(POOL).whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null) {
                            LOG.warning("Failed to load texture", exception);
                        } else if (result != null && result.skin() != null && result.skin().image() != null) {
                            Map<String, String> metadata;
                            if (result.model() != null) {
                                metadata = singletonMap("model", result.model().modelName);
                            } else {
                                metadata = emptyMap();
                            }

                            binding.set(new LoadedTexture(result.skin().image(), metadata));
                        }
                    }).start();
                }
            };

            listener.invalidated(offlineAccount);

            binding.addListener(new Holder<>(listener));
            offlineAccount.addListener(new WeakInvalidationListener(listener));

            return binding;
        } else {
            return BindingMapping.of(account.getTextures())
                    .asyncMap(textures -> {
                        if (textures.isPresent()) {
                            Texture texture = textures.get().get(TextureType.SKIN);
                            if (texture != null && StringUtils.isNotBlank(texture.url())) {
                                return CompletableFuture.supplyAsync(() -> {
                                    try {
                                        return loadTexture(texture);
                                    } catch (Throwable e) {
                                        LOG.warning("Failed to load texture " + texture.url() + ", using fallback texture", e);
                                        return uuidFallback;
                                    }
                                }, POOL);
                            }
                        }

                        return CompletableFuture.completedFuture(uuidFallback);
                    }, uuidFallback);
        }
    }

    // ====

    // ==== Avatar ====
    public static void drawAvatar(Canvas canvas, Image skin) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int size = (int) canvas.getWidth();
        int scale = (int) skin.getWidth() / 64;
        int faceOffset = (int) Math.round(size / 18.0);

        g.setImageSmoothing(false);
        drawAvatar(g, skin, size, scale, faceOffset);
    }

    private static void drawAvatar(GraphicsContext g, Image skin, int size, int scale, int faceOffset) {
        g.drawImage(skin,
                8 * scale, 8 * scale, 8 * scale, 8 * scale,
                faceOffset, faceOffset, size - 2 * faceOffset, size - 2 * faceOffset);
        g.drawImage(skin,
                40 * scale, 8 * scale, 8 * scale, 8 * scale,
                0, 0, size, size);
    }

    private record SkinBindingChangeListener(WeakReference<Canvas> canvasRef,
                                             ObservableValue<LoadedTexture> binding) implements ChangeListener<LoadedTexture> {
        static final WeakHashMap<Canvas, SkinBindingChangeListener> hole = new WeakHashMap<>();

        private SkinBindingChangeListener(Canvas canvasRef, ObservableValue<LoadedTexture> binding) {
            this(new WeakReference<>(canvasRef), binding);
        }

        @Override
        public void changed(ObservableValue<? extends LoadedTexture> observable,
                            LoadedTexture oldValue, LoadedTexture loadedTexture) {
            Canvas canvas = canvasRef.get();
            if (canvas != null)
                drawAvatar(canvas, loadedTexture.image);
        }
    }

    public static void fxAvatarBinding(Canvas canvas, ObservableValue<LoadedTexture> skinBinding) {
        synchronized (SkinBindingChangeListener.hole) {
            SkinBindingChangeListener oldListener = SkinBindingChangeListener.hole.remove(canvas);
            if (oldListener != null)
                oldListener.binding.removeListener(oldListener);

            SkinBindingChangeListener listener = new SkinBindingChangeListener(canvas, skinBinding);
            listener.changed(skinBinding, null, skinBinding.getValue());
            skinBinding.addListener(listener);

            SkinBindingChangeListener.hole.put(canvas, listener);
        }
    }

    public static void bindAvatar(Canvas canvas, YggdrasilService service, UUID uuid) {
        fxAvatarBinding(canvas, skinBinding(service, uuid));
    }

    public static void bindAvatar(Canvas canvas, Account account) {
        if (account instanceof YggdrasilAccount || account instanceof MicrosoftAccount || account instanceof OfflineAccount)
            fxAvatarBinding(canvas, skinBinding(account));
        else {
            unbindAvatar(canvas);
            drawAvatar(canvas, getDefaultSkin(account.getProfileID()).image);
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
