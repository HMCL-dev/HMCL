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
package org.jackhuang.hmcl.ui.versions;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;
import static org.jackhuang.hmcl.util.Lang.handleUncaught;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameItem extends Control {

    private static final ThreadPoolExecutor POOL_VERSION_RESOLVE = threadPool("VersionResolve", true, 1, 1, TimeUnit.SECONDS);

    private final Profile profile;
    private final String version;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty tag = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();

    public GameItem(Profile profile, String id) {
        this.profile = profile;
        this.version = id;

        // GameVersion.minecraftVersion() is a time-costing job (up to ~200 ms)
        CompletableFuture.supplyAsync(() -> GameVersion.minecraftVersion(profile.getRepository().getVersionJar(id)).orElse(i18n("message.unknown")), POOL_VERSION_RESOLVE)
                .thenAcceptAsync(game -> {
                    StringBuilder libraries = new StringBuilder(game);
                    LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(id));
                    for (LibraryAnalyzer.LibraryMark mark : analyzer) {
                        String libraryId = mark.getLibraryId();
                        String libraryVersion = mark.getLibraryVersion();
                        if (libraryId.equals(MINECRAFT.getPatchId())) continue;
                        if (I18n.hasKey("install.installer." + libraryId)) {
                            libraries.append(", ").append(i18n("install.installer." + libraryId));
                            if (libraryVersion != null)
                                libraries.append(": ").append(modifyVersion("", libraryVersion.replaceAll("(?i)" + libraryId, "")));
                        }
                    }

                    subtitle.set(libraries.toString());
                }, Platform::runLater)
                .exceptionally(handleUncaught);

        CompletableFuture.runAsync(() -> {
            try {
                ModpackConfiguration<?> config = profile.getRepository().readModpackConfiguration(version);
                if (config == null) return;
                tag.set(config.getVersion());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to read modpack configuration from ", e);
            }
        }, Platform::runLater)
                .exceptionally(handleUncaught);

        title.set(id);
        image.set(profile.getRepository().getVersionIconImage(version));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameItemSkin(this);
    }

    public Profile getProfile() {
        return profile;
    }

    public String getVersion() {
        return version;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty tagProperty() {
        return tag;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }
}
