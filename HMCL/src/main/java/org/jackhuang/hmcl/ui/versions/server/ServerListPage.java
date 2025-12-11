/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions.server;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.versions.VersionPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ServerListPage extends ListPageBase<ServerListItem> implements VersionPage.VersionLoadable {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);
    private final BooleanProperty showHide = new SimpleBooleanProperty(this, "showHide", false);

    Profile profile;
    String version;
    private List<ServerListItem> serverListItems = Collections.emptyList();

    public ServerListPage() {
        showAll.addListener(e -> updateFilter());
        showHide.addListener(e -> updateFilter());
    }

    public static List<ServerData> readServersFromDat(Path datFile) {
        List<ServerData> set = new ArrayList<>();
        if (datFile != null && Files.exists(datFile)) {
            try {
                CompoundTag tags = NBTIO.readFile(datFile.toFile(), false, false);
                Tag serversTag = tags.get("servers");
                if (serversTag instanceof ListTag st) {
                    for (Tag tag : st) {
                        if (tag instanceof CompoundTag cTag) {
                            set.add(ServerData.fromCompoundTag(cTag));
                        }
                    }
                }
            } catch (IOException e) {
                LOG.error("Failed to read servers.dat file.", e);
            }
        }
        return set;
    }

    public static void saveServerToDat(Path datFile, List<ServerData> saveServerData) {
        ListTag tag = new ListTag("servers", CompoundTag.class);
        for (ServerData server : saveServerData) {
            CompoundTag serverTag = new CompoundTag("");
            server.writeToCompoundTag(serverTag);
            tag.add(serverTag);
        }

        CompoundTag root = new CompoundTag("");
        root.put(tag);

        try {
            NBTIO.writeFile(root, datFile.toFile(), false, false);
        } catch (IOException e) {
            LOG.error("Failed to write servers.dat file.", e);
        }
    }

    private void updateFilter() {
        itemsProperty().setAll(serverListItems.stream()
                .filter(item -> showAll.get() || (item.serverDatPath.equals(profile.getRepository().getServersDatFilePath(version))))
                .filter(item -> showHide.get() || !item.serverData.hidden)
                .toList()
        );
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.version = id;

        refresh();
    }

    void refresh() {
        if (profile == null || version == null)
            return;

        setLoading(true);

        Task.supplyAsync(() -> {
            List<ServerListItem> list = new ArrayList<>();
            for (Version version : profile.getRepository().getVersions()) {
                Path serverDat = profile.getRepository().getServersDatFilePath(version.getId());
                List<ServerData> dataList = readServersFromDat(serverDat);
                for (int i = 0; i < dataList.size(); i++) {
                    list.add(new ServerListItem(serverDat, i, switch (profile.getRepository().getGameDirectoryType(version.getId())) {
                        case CUSTOM, ROOT_FOLDER -> i18n("server.tag.public");
                        case VERSION_FOLDER -> version.getId();
                    }, this, dataList.get(i)));
                }
            }
            return list;
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            serverListItems = result.stream().distinct().toList();
            setLoading(false);
            if (exception == null) {
                updateFilter();
            } else {
                LOG.warning("Failed to load servers.", exception);
            }
        }).start();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ToolbarListPageSkin<>(this) {
            @Override
            protected List<Node> initializeToolbar(ServerListPage page) {
                JFXCheckBox chkShowAll = new JFXCheckBox();
                chkShowAll.setText(i18n("servers.show_all"));
                chkShowAll.selectedProperty().bindBidirectional(showAll);

                JFXCheckBox chkShowHide = new JFXCheckBox();
                chkShowHide.setText(i18n("servers.show_hide"));
                chkShowHide.selectedProperty().bindBidirectional(showHide);

                return List.of(
                        chkShowAll,
                        chkShowHide,
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, () -> refresh())
                );
            }
        };
    }
}
