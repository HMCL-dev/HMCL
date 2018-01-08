/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.optifine;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author huangyuhui
 */
public final class OptiFineVersionList extends VersionList<Void> {

    private static final Pattern PATTERN = Pattern.compile("OptiFine (.*?) ");
    public static final OptiFineVersionList INSTANCE = new OptiFineVersionList();

    private OptiFineVersionList() {
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        GetTask task = new GetTask(NetworkUtils.toURL("http://optifine.net/downloads"));
        return new Task() {
            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() throws Exception {
                versions.clear();

                String html = task.getResult().replace("&nbsp;", " ").replace("&gt;", ">").replace("&lt;", "<").replace("<br>", "<br />");

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = factory.newDocumentBuilder();
                Document doc = db.parse(new ByteArrayInputStream(html.getBytes("UTF-8")));
                Element r = doc.getDocumentElement();
                NodeList tables = r.getElementsByTagName("table");
                for (int i = 0; i < tables.getLength(); i++) {
                    Element e = (Element) tables.item(i);
                    if ("downloadTable".equals(e.getAttribute("class"))) {
                        NodeList tr = e.getElementsByTagName("tr");
                        for (int k = 0; k < tr.getLength(); k++) {
                            NodeList downloadLine = ((Element) tr.item(k)).getElementsByTagName("td");
                            String url = null, version = null, gameVersion = null;
                            for (int j = 0; j < downloadLine.getLength(); j++) {
                                Element td = (Element) downloadLine.item(j);
                                if (td.getAttribute("class") != null && td.getAttribute("class").startsWith("downloadLineMirror"))
                                    url = ((Element) td.getElementsByTagName("a").item(0)).getAttribute("href");
                                if (td.getAttribute("class") != null && td.getAttribute("class").startsWith("downloadLineFile"))
                                    version = td.getTextContent();
                            }
                            if (version == null || url == null)
                                continue;

                            Matcher matcher = PATTERN.matcher(version);
                            while (matcher.find())
                                gameVersion = matcher.group(1);
                            if (gameVersion == null)
                                continue;
                            versions.put(gameVersion, new RemoteVersion<>(gameVersion, version, url, null));
                        }
                    }
                }
            }
        };
    }

}
