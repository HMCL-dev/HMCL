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
package org.jackhuang.hmcl.ui.modpack;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.swing.JComponent;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.mod.ModpackManager;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.setting.Config;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.util.HMCLMinecraftService;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.Utils;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.IOUtils;
import org.jackhuang.hmcl.util.sys.ZipEngine;
import org.jackhuang.hmcl.util.net.WebPage;
import org.jackhuang.hmcl.util.ui.checktree.CheckBoxTreeNode;
import org.jackhuang.hmcl.util.ui.wizard.spi.DeferredWizardResult;
import org.jackhuang.hmcl.util.ui.wizard.spi.ResultProgressHandle;
import org.jackhuang.hmcl.util.ui.wizard.spi.Summary;
import org.jackhuang.hmcl.util.ui.wizard.spi.WizardBranchController;
import org.jackhuang.hmcl.util.ui.wizard.spi.WizardController;
import org.jackhuang.hmcl.util.ui.wizard.spi.WizardException;
import org.jackhuang.hmcl.util.ui.wizard.spi.WizardPanelProvider;

/**
 *
 * @author huangyuhui
 */
public class ModpackWizard extends WizardBranchController {

    static void process(CheckBoxTreeNode node, String basePath, List<String> list) {
        if (node == null)
            return;
        if (node.isSelected()) {
            if (basePath.length() > "minecraft/".length())
                list.add(basePath.substring("minecraft/".length()));
            return;
        }
        Enumeration<CheckBoxTreeNode> e = node.children();
        for (; e.hasMoreElements();) {
            CheckBoxTreeNode n = e.nextElement();
            String s;
            if (n.getUserObject() instanceof Pair)
                s = ((Pair<String, String>) n.getUserObject()).key;
            else
                s = n.getUserObject().toString();
            process(n, basePath + "/" + s, list);
        }
    }

    public ModpackWizard(Profile profile) {
        super(new WizardPanelProvider(C.i18n("modpack.wizard"), new String[] { C.i18n("modpack.wizard.step.1"), C.i18n("modpack.wizard.step.2"), C.i18n("modpack.wizard.step.3") }, new String[] { C.i18n("modpack.wizard.step.1.title"), C.i18n("modpack.wizard.step.2.title"), C.i18n("modpack.wizard.step.3.title") }) {

            @Override
            protected Object finish(Map settings) throws WizardException {
                return new DeferredWizardResult(false) {
                    @Override
                    public void start(Map settings, ResultProgressHandle progress) {
                        progress.setBusy("Processing modpack");
                        ArrayList<String> blackList = new ArrayList<>(ModpackManager.MODPACK_BLACK_LIST);
                        CheckBoxTreeNode root = (CheckBoxTreeNode) settings.get("blackList");
                        process(root, "minecraft", blackList);
                        HashMap map = new HashMap();
                        map.put("name", (String) settings.get(ModpackInitializationPanel.KEY_MODPACK_NAME));

                        if (settings.containsKey(ModpackDescriptionPanel.KEY_MODPACK_DESCRITION))
                            try {
                                map.put("description", new org.markdown4j.Markdown4jProcessor().process((String) settings.get(ModpackDescriptionPanel.KEY_MODPACK_DESCRITION)));
                            } catch (IOException ex) {
                                progress.failed(C.i18n("modpack.export_error") + ": " + StrUtils.getStackTrace(ex), true);
                            }
                        try {
                            String gameVersion = (String) settings.get(ModpackInitializationPanel.KEY_GAME_VERSION);
                            String strLocation = (String) settings.get(ModpackInitializationPanel.KEY_MODPACK_LOCATION);
                            if (!strLocation.endsWith(".zip"))
                                strLocation += ".zip";
                            File loc = new File(strLocation);
                            File modpack = loc;
                            if ((Boolean) settings.get(ModpackInitializationPanel.KEY_INCLUDING_LAUNCHER))
                                modpack = new File(loc.getAbsolutePath() + ".temp");
                            ModpackManager.export(modpack, profile.service().version(), gameVersion, blackList, map,
                                                  t -> t.putTextFile(C.GSON.toJson(((HMCLMinecraftService) profile.service()).getVersionSetting(gameVersion)), "minecraft/hmclversion.cfg"));
                            String summary = "<html>" + C.i18n("modpack.export_finished") + ": " + loc.getAbsolutePath();
                            boolean including = false;
                            if ((Boolean) settings.get(ModpackInitializationPanel.KEY_INCLUDING_LAUNCHER)) {
                                boolean flag = true;
                                try (ZipEngine engine = new ZipEngine(loc)) {
                                    Config s = new Config();
                                    if (!IOUtils.isAbsolutePath(Settings.getInstance().getBgpath()))
                                        s.setBgpath(Settings.getInstance().getBgpath());
                                    s.setDownloadType(Settings.getInstance().getDownloadType());
                                    engine.putTextFile(C.GSON.toJson(s), "/Users/rhj/.hmcl/hmcl.json");
                                    engine.putFile(modpack, "modpack.zip");
                                    File bg = new File("bg").getAbsoluteFile();
                                    if (bg.isDirectory())
                                        engine.putDirectory(bg);
                                    bg = new File("background.png").getAbsoluteFile();
                                    if (bg.isFile())
                                        engine.putFile(bg, "background.png");
                                    bg = new File("background.jpg").getAbsoluteFile();
                                    if (bg.isFile())
                                        engine.putFile(bg, "background.jpg");
                                    for (URL u : Utils.getURL())
                                        try {
                                            File f = new File(u.toURI());
                                            if (f.getName().endsWith(".exe") || f.getName().endsWith(".jar"))
                                                engine.putFile(f, f.getName());
                                        } catch (IOException | URISyntaxException e) {
                                            HMCLog.err("Failed to add launcher files.", e);
                                            flag = false;
                                            break;
                                        }
                                }
                                if (flag) {
                                    including = true;
                                    if (!modpack.delete())
                                        HMCLog.warn("Failed to delete modpack.zip.temp, maybe the file is in using.");
                                }
                            }
                            summary += "<br/>" + C.i18n(including ? "modpack.included_launcher" : "modpack.not_included_launcher") + "</html>";
                            progress.finished(new Summary(new WebPage(summary), null));
                        } catch (IOException | GameException ex) {
                            HMCLog.err("Failed to export modpack", ex);
                            progress.failed(C.i18n("modpack.export_error") + ": " + StrUtils.getStackTrace(ex), true);
                        }
                    }
                };
            }

            @Override

            protected JComponent createPanel(WizardController controller, String id, Map settings) {
                switch (indexOfStep(id)) {
                case 0:
                    Vector<String> s = new Vector<>(profile.service().version().getVersionCount());
                    for (MinecraftVersion v : profile.service().version().getVersions())
                        if (!v.hidden)
                            s.add(v.id);

                    controller.setForwardNavigationMode(WizardController.MODE_CAN_CONTINUE);

                    return new ModpackInitializationPanel(controller, settings, s, profile.getSelectedVersion());
                case 1:
                    controller.setForwardNavigationMode(WizardController.MODE_CAN_CONTINUE_OR_FINISH);

                    return new ModpackFileSelectionPanel(controller, settings, profile.service().baseDirectory(), ModpackManager.MODPACK_PREDICATE);
                case 2:
                    controller.setForwardNavigationMode(WizardController.MODE_CAN_FINISH);

                    return new ModpackDescriptionPanel(controller, settings);
                default:
                    throw new IllegalArgumentException(id);
                }
            }
        });
    }

    @Override
    protected WizardPanelProvider getPanelProviderForStep(String step, Map settings) {
        return null;
    }

}
