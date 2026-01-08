/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;
import static org.jackhuang.hmcl.util.Lang.handleUncaught;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class GameItem2 {
    private static final ThreadPoolExecutor POOL_VERSION_RESOLVE = threadPool("VersionResolve", true, 1, 1, TimeUnit.SECONDS);

    private final Profile profile;
    private final String id;

    private boolean initialized = false;
    private StringProperty title;
    private StringProperty tag;
    private StringProperty subtitle;
    private ObjectProperty<Image> image;

    public GameItem2(Profile profile, String id) {
        this.profile = profile;
        this.id = id;
    }

    private void init() {
        if (initialized)
            return;

        initialized = true;
        title = new SimpleStringProperty();
        tag = new SimpleStringProperty();
        subtitle = new SimpleStringProperty();
        image = new SimpleObjectProperty<>();

        // GameVersion.minecraftVersion() is a time-costing job (up to ~200 ms)
        CompletableFuture.supplyAsync(() -> profile.getRepository().getGameVersion(id), POOL_VERSION_RESOLVE)
                .thenAcceptAsync(game -> {
                    StringBuilder libraries = new StringBuilder(game.orElse(i18n("message.unknown")));
                    LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(id), game.orElse(null));
                    for (LibraryAnalyzer.LibraryMark mark : analyzer) {
                        String libraryId = mark.getLibraryId();
                        String libraryVersion = mark.getLibraryVersion();
                        if (libraryId.equals(MINECRAFT.getPatchId())) continue;
                        if (I18n.hasKey("install.installer." + libraryId)) {
                            libraries.append(", ").append(i18n("install.installer." + libraryId));
                            if (libraryVersion != null)
                                libraries.append(": ").append(libraryVersion.replaceAll("(?i)" + libraryId, ""));
                        }
                    }

                    subtitle.set(libraries.toString());
                }, Platform::runLater)
                .exceptionally(handleUncaught);

        CompletableFuture.runAsync(() -> {
                    try {
                        ModpackConfiguration<?> config = profile.getRepository().readModpackConfiguration(id);
                        if (config == null) return;
                        tag.set(config.getVersion());
                    } catch (IOException | JsonParseException e) {
                        LOG.warning("Failed to read modpack configuration from " + id, e);
                    }
                }, Platform::runLater)
                .exceptionally(handleUncaught);

        title.set(id);
        image.set(profile.getRepository().getVersionIconImage(id));
    }

    public ReadOnlyStringProperty titleProperty() {
        init();
        return title;
    }

    public ReadOnlyStringProperty tagProperty() {
        init();
        return tag;
    }

    public ReadOnlyStringProperty subtitleProperty() {
        init();
        return subtitle;
    }

    public ReadOnlyObjectProperty<Image> imageProperty() {
        init();
        return image;
    }
}
