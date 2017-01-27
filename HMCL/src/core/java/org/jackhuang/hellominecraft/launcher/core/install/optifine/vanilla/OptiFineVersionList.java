/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.core.install.optifine.vanilla;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.core.install.optifine.OptiFineVersion;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;
import org.jackhuang.hellominecraft.util.tasks.download.HTTPGetTask;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author huangyuhui
 */
public class OptiFineVersionList extends InstallerVersionList {

    private static OptiFineVersionList instance;

    public static OptiFineVersionList getInstance() {
        if (null == instance)
            instance = new OptiFineVersionList();
        return instance;
    }

    public ArrayList<OptiFineVersion> root = new ArrayList();
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public Task refresh(String[] sss) {
        if (versions != null)
            return null;
        return new TaskInfo(C.i18n("install.optifine.get_list")) {
            HTTPGetTask task = new HTTPGetTask("http://optifine.net/downloads");

            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(task.setTag("Optifine Download Site"));
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
                if (!areDependTasksSucceeded)
                    return;
                String content = task.getResult();
                versionMap = new HashMap<>();
                versions = new ArrayList<>();

                content = content.replace("&nbsp;", " ").replace("&gt;", ">").replace("&lt;", "<").replace("<br>", "<br />");

                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = factory.newDocumentBuilder();
                    Document doc = db.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
                    Element r = doc.getDocumentElement();
                    NodeList tables = r.getElementsByTagName("table");
                    for (int i = 0; i < tables.getLength(); i++) {
                        Element e = (Element) tables.item(i);
                        if ("downloadTable".equals(e.getAttribute("class"))) {
                            NodeList tr = e.getElementsByTagName("tr");
                            for (int k = 0; k < tr.getLength(); k++) {
                                NodeList downloadLine = ((Element) tr.item(k)).getElementsByTagName("td");
                                OptiFineVersion v = new OptiFineVersion();
                                for (int j = 0; j < downloadLine.getLength(); j++) {
                                    Element td = (Element) downloadLine.item(j);
                                    if (StrUtils.startsWith(td.getAttribute("class"), "downloadLineMirror"))
                                        v.setMirror(((Element) td.getElementsByTagName("a").item(0)).getAttribute("href"));
                                    if (StrUtils.startsWith(td.getAttribute("class"), "downloadLineDownload"))
                                        v.setDownloadLink(((Element) td.getElementsByTagName("a").item(0)).getAttribute("href"));
                                    if (StrUtils.startsWith(td.getAttribute("class"), "downloadLineDate"))
                                        v.setDate(td.getTextContent());
                                    if (StrUtils.startsWith(td.getAttribute("class"), "downloadLineFile"))
                                        v.setVersion(td.getTextContent());
                                }
                                if (StrUtils.isBlank(v.getMCVersion())) {
                                    Pattern p = Pattern.compile("OptiFine (.*?) ");
                                    Matcher m = p.matcher(v.getVersion());
                                    while (m.find())
                                        v.setMCVersion(StrUtils.formatVersion(m.group(1)));
                                }
                                InstallerVersion iv = new InstallerVersion(v.getVersion(), StrUtils.formatVersion(v.getMCVersion()));
                                iv.installer = iv.universal = v.getMirror();
                                root.add(v);
                                versions.add(iv);

                                List<InstallerVersion> ivl = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.getMCVersion()));
                                ivl.add(iv);
                            }
                        }
                    }
                } catch (ParserConfigurationException | SAXException | IOException | DOMException ex) {
                    throw new RuntimeException(ex);
                }

                Collections.sort(versions, InstallerVersionComparator.INSTANCE);
            }
        };
    }

    @Override
    public String getName() {
        return "OptiFine - OptiFine Official Site";
    }

    @Override
    public List<InstallerVersion> getVersionsImpl(String mcVersion) {
        if (versions == null || versionMap == null)
            return null;
        if (StrUtils.isBlank(mcVersion))
            return versions;
        List c = versionMap.get(mcVersion);
        if (c == null)
            return versions;
        Collections.sort(c, InstallerVersionComparator.INSTANCE);
        return c;
    }

}
