/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.download.InstallerWizardProvider;

import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallerListPage extends ListPage<InstallerItem> {
    private Profile profile;
    private String versionId;
    private Version version;

    public void loadVersion(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;
        this.version = profile.getRepository().getResolvedVersion(versionId);

        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);

        Function<Library, Consumer<InstallerItem>> removeAction = library -> x -> {
            LinkedList<Library> newList = new LinkedList<>(version.getLibraries());
            newList.remove(library);
            new MaintainTask(version.setLibraries(newList))
                    .then(variables -> new VersionJsonSaveTask(profile.getRepository(), variables.get(MaintainTask.ID)))
                    .with(profile.getRepository().refreshVersionsAsync())
                    .with(Task.of(Schedulers.javafx(), () -> loadVersion(this.profile, this.versionId)))
                    .start();
        };

        analyzer.getForge().ifPresent(library -> itemsProperty().add(new InstallerItem("Forge", library.getVersion(), removeAction.apply(library))));
        analyzer.getLiteLoader().ifPresent(library -> itemsProperty().add(new InstallerItem("LiteLoader", library.getVersion(), removeAction.apply(library))));
        analyzer.getOptiFine().ifPresent(library -> itemsProperty().add(new InstallerItem("OptiFine", library.getVersion(), removeAction.apply(library))));
    }

    @Override
    public void add() {
        Optional<String> gameVersion = GameVersion.minecraftVersion(profile.getRepository().getVersionJar(version));

        if (!gameVersion.isPresent())
            Controllers.dialog(i18n("version.cannot_read"));
        else
            Controllers.getDecorator().startWizard(new InstallerWizardProvider(profile, gameVersion.get(), version));
    }
}
