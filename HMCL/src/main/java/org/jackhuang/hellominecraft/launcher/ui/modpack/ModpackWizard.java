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
package org.jackhuang.hellominecraft.launcher.ui.modpack;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.mod.ModpackManager;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.Pair;
import org.jackhuang.hellominecraft.util.Utils;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.ZipEngine;
import org.jackhuang.hellominecraft.util.ui.checktree.CheckBoxTreeNode;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.DeferredWizardResult;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.ResultProgressHandle;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.Summary;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.WizardBranchController;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.WizardController;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.WizardException;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.WizardPanelProvider;

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
            String s = null;
            if (n.getUserObject() instanceof Pair)
                s = ((Pair<String, String>) n.getUserObject()).key;
            else
                s = n.getUserObject().toString();
            process(n, basePath + "/" + s, list);
        }
    }

    public ModpackWizard(IMinecraftService service) {
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
                            map.put("description", (String) settings.get(ModpackDescriptionPanel.KEY_MODPACK_DESCRITION));
                        try {
                            File loc = new File((String) settings.get(ModpackInitializationPanel.KEY_MODPACK_LOCATION));
                            File modpack = loc;
                            if ((Boolean) settings.get(ModpackInitializationPanel.KEY_INCLUDING_LAUNCHER))
                                modpack = File.createTempFile("hmcl", ".zip");
                            ModpackManager.export(modpack,
                                                  service.version(),
                                                  (String) settings.get(ModpackInitializationPanel.KEY_GAME_VERSION),
                                                  blackList, map);
                            String summary = C.i18n("modpack.export_finished") + ": " + loc.getAbsolutePath();
                            boolean including = false;
                            if ((Boolean) settings.get(ModpackInitializationPanel.KEY_INCLUDING_LAUNCHER)) {
                                boolean flag = true;
                                ZipEngine engine = new ZipEngine(loc);
                                engine.putFile(loc, "modpack.zip");
                                for (URL u : Utils.getURL())
                                    try {
                                        File f = new File(u.toURI());
                                        if (f.getName().endsWith(".exe") || f.getName().endsWith(".jar"))
                                            engine.putFile(f, f.getName());
                                    } catch (Exception e) {
                                        HMCLog.err("Failed to add launcher files.", e);
                                        flag = false;
                                        break;
                                    }
                                engine.closeFile();
                                if (!flag) {
                                    loc.delete();
                                    FileUtils.copyFile(modpack, loc);
                                } else
                                    including = true;
                            }
                            summary += "<br/>" + C.i18n(including ? "modpack.included_launcher" : "modpack.not_included_launcher");
                            progress.finished(new Summary(summary, null));
                        } catch (IOException | GameException ex) {
                            HMCLog.err("Failed to export modpack", ex);
                            progress.failed(C.i18n("modpack.export_error") + ": " + ex.getClass().getName() + ", " + ex.getLocalizedMessage(), true);
                        }
                    }
                };
            }

            @Override
            protected JComponent createPanel(WizardController controller, String id, Map settings) {
                switch (indexOfStep(id)) {
                case 0:
                    String[] s = new String[service.version().getVersionCount()];
                    Iterator<MinecraftVersion> it = service.version().getVersions().iterator();
                    for (int i = 0; i < s.length; i++)
                        s[i] = it.next().id;

                    controller.setForwardNavigationMode(WizardController.MODE_CAN_CONTINUE);

                    return new ModpackInitializationPanel(controller, settings, s);
                case 1:
                    controller.setForwardNavigationMode(WizardController.MODE_CAN_CONTINUE_OR_FINISH);

                    return new ModpackFileSelectionPanel(controller, settings, service.baseDirectory(), ModpackManager.MODPACK_PREDICATE);
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
