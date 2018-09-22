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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author huangyuhui
 */
public final class LiteLoaderVersionList extends VersionList<LiteLoaderRemoteVersion> {

    public static final LiteLoaderVersionList INSTANCE = new LiteLoaderVersionList();

    private LiteLoaderVersionList() {
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        GetTask task = new GetTask(NetworkUtils.toURL(downloadProvider.injectURL(LITELOADER_LIST)));
        return new Task() {
            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() {
                lock.writeLock().lock();

                try {
                    LiteLoaderVersionsRoot root = JsonUtils.GSON.fromJson(task.getResult(), LiteLoaderVersionsRoot.class);
                    versions.clear();

                    for (Map.Entry<String, LiteLoaderGameVersions> entry : root.getVersions().entrySet()) {
                        String gameVersion = entry.getKey();
                        LiteLoaderGameVersions liteLoader = entry.getValue();
                        Optional<String> gg = VersionNumber.parseVersion(gameVersion);
                        if (!gg.isPresent())
                            continue;
                        doBranch(gg.get(), gameVersion, liteLoader.getRepoitory(), liteLoader.getArtifacts(), false);
                        doBranch(gg.get(), gameVersion, liteLoader.getRepoitory(), liteLoader.getSnapshots(), true);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }

            private void doBranch(String key, String gameVersion, LiteLoaderRepository repository, LiteLoaderBranch branch, boolean snapshot) {
                if (branch == null || repository == null)
                    return;

                for (Map.Entry<String, LiteLoaderVersion> entry : branch.getLiteLoader().entrySet()) {
                    String branchName = entry.getKey();
                    LiteLoaderVersion v = entry.getValue();
                    if ("latest".equals(branchName))
                        continue;

                    String version = v.getVersion();
                    String url = repository.getUrl() + "com/mumfrey/liteloader/" + gameVersion + "/" + v.getFile();
                    if (snapshot) {
                        try {
                            version = version.replace("SNAPSHOT", getLatestSnapshotVersion(repository.getUrl() + "com/mumfrey/liteloader/" + v.getVersion() + "/"));
                            url = repository.getUrl() + "com/mumfrey/liteloader/" + v.getVersion() + "/liteloader-" + version + "-release.jar";
                        } catch (Exception ignore) {
                        }
                    }

                    versions.put(key, new LiteLoaderRemoteVersion(gameVersion,
                            version, downloadProvider.injectURL(url),
                            v.getTweakClass(), v.getLibraries()
                    ));
                }
            }
        };
    }

    public static final String LITELOADER_LIST = "http://dl.liteloader.com/versions/versions.json";

    private static String getLatestSnapshotVersion(String repo) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(repo + "maven-metadata.xml");
        Element r = doc.getDocumentElement();
        Element snapshot = (Element) r.getElementsByTagName("snapshot").item(0);
        Node timestamp = snapshot.getElementsByTagName("timestamp").item(0);
        Node buildNumber = snapshot.getElementsByTagName("buildNumber").item(0);
        return timestamp.getTextContent() + "-" + buildNumber.getTextContent();
    }
}
