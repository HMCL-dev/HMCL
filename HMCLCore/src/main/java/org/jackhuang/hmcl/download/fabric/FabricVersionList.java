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
package org.jackhuang.hmcl.download.fabric;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class FabricVersionList extends VersionList<FabricRemoteVersion> {

    public static final FabricVersionList INSTANCE = new FabricVersionList();

    private FabricVersionList() {
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task<?> refreshAsync(DownloadProvider downloadProvider) {
        return new Task<Void>() {
            @Override
            public void execute() throws IOException, XMLStreamException {
                List<String> gameVersions = getGameVersions(META_URL);
                List<String> loaderVersions = getVersions(FABRIC_MAVEN_URL, FABRIC_PACKAGE_NAME, FABRIC_JAR_NAME);

                lock.writeLock().lock();

                try {
                    for (String gameVersion : gameVersions)
                        for (String loaderVersion : loaderVersions)
                            versions.put(gameVersion, new FabricRemoteVersion(gameVersion, loaderVersion, ""));
                } finally {
                    lock.writeLock().unlock();
                }
            }
        };
    }

    private static final String META_URL = "https://meta.fabricmc.net/v2/versions/game";
    private static final String FABRIC_MAVEN_URL = "https://maven.fabricmc.net/";
    private static final String FABRIC_PACKAGE_NAME = "net/fabricmc";
    private static final String FABRIC_JAR_NAME = "fabric-loader";

    private List<String> getVersions(String mavenServerURL, String packageName, String jarName) throws IOException, XMLStreamException {
        List<String> versions = new ArrayList<>();
        URL url = new URL(mavenServerURL + packageName + "/" + jarName + "/maven-metadata.xml");
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(url.openStream());

        while(reader.hasNext()) {
            if (reader.next() == 1 && reader.getLocalName().equals("version")) {
                String text = reader.getElementText();
                versions.add(text);
            }
        }

        reader.close();
        Collections.reverse(versions);
        return versions;
    }

    private List<String> getGameVersions(String metaUrl) throws IOException {
        String json = NetworkUtils.doGet(NetworkUtils.toURL(metaUrl));
        return JsonUtils.GSON.<ArrayList<GameVersion>>fromJson(json, new TypeToken<ArrayList<GameVersion>>() {
        }.getType()).stream().map(GameVersion::getVersion).collect(Collectors.toList());
    }

    private static class GameVersion {
        private final String version;
        private final boolean stable;

        public GameVersion() {
            this("", false);
        }

        public GameVersion(String version, boolean stable) {
            this.version = version;
            this.stable = stable;
        }

        public String getVersion() {
            return version;
        }

        public boolean isStable() {
            return stable;
        }
    }
}
