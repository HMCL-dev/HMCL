/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla;

import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.OptiFineVersion;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author hyh
 */
public class OptiFineVersionList extends InstallerVersionList {
    private static OptiFineVersionList instance;
    public static OptiFineVersionList getInstance() {
	if(null == instance)
	    instance = new OptiFineVersionList();
	return instance;
    }


    public ArrayList<OptiFineVersion> root = new ArrayList();
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] sss) throws Exception {
        String content = NetUtils.doGet("http://optifine.net/downloads");
        if(versions != null) return;
	versionMap = new HashMap<String, List<InstallerVersion>>();
	versions = new ArrayList<InstallerVersion>();
        
        content = content.replace("&nbsp;", " ").replace("&gt;", ">").replace("&lt;", "<");
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = factory.newDocumentBuilder();
            Document doc = db.parse(new StringBufferInputStream(content));
            Element r = doc.getDocumentElement();
            NodeList tables = r.getElementsByTagName("table");
            for(int i = 0; i < tables.getLength(); i++) {
                Element e = (Element)tables.item(i);
                if("downloadTable".equals(e.getAttribute("class"))) {
                    NodeList tr = e.getElementsByTagName("tr");
                    for(int k = 0; k < tr.getLength(); k++) {
                        NodeList downloadLine = ((Element)tr.item(k)).getElementsByTagName("td");
                        OptiFineVersion v = new OptiFineVersion();
                        for(int j = 0; j < downloadLine.getLength(); j++) {
                            Element td = (Element)downloadLine.item(j);
                            if(StrUtils.startsWith(td.getAttribute("class"), "downloadLineMirror")) {
                                v.mirror = ((Element)td.getElementsByTagName("a").item(0)).getAttribute("href");
                            }
                            if(StrUtils.startsWith(td.getAttribute("class"), "downloadLineDownload")) {
                                v.dl = ((Element)td.getElementsByTagName("a").item(0)).getAttribute("href");
                            }
                            if(StrUtils.startsWith(td.getAttribute("class"), "downloadLineDate")) {
                                v.date = td.getTextContent();
                            }
                            if(StrUtils.startsWith(td.getAttribute("class"), "downloadLineFile")) {
                                v.ver = td.getTextContent();
                            }
                        }
                        if(StrUtils.isBlank(v.mcver)) {
                            Pattern p = Pattern.compile("OptiFine (.*?) ");
                            Matcher m = p.matcher(v.ver);
                            while(m.find()) v.mcver = StrUtils.formatVersion(m.group(1));
                        }
                        InstallerVersion iv = new InstallerVersion(v.ver, StrUtils.formatVersion(v.mcver));
                        iv.installer = iv.universal = v.mirror;
                        root.add(v);
                        versions.add(iv);


                        List<InstallerVersion> ivl = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.mcver));
                        ivl.add(iv);
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        Collections.sort(versions, InstallerVersionComparator.INSTANCE);
    }
    
    @Override
    public String getName() {
        return "OptiFine - OptiFine Official Site";
    }

    @Override
    public List<InstallerVersion> getVersions(String mcVersion) {
        if (versions == null || versionMap == null) return null;
        if(StrUtils.isBlank(mcVersion)) return versions;
	List c = versionMap.get(mcVersion);
	if(c == null) return versions;
        Collections.sort(c, InstallerVersionComparator.INSTANCE);
	return c;
    }
    
}
