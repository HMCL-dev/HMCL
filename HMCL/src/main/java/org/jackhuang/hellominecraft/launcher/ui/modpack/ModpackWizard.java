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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.mod.ModpackManager;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
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
        if (node.isSelected()) {
            if (basePath.length() > "minecraft/".length())
                list.add(basePath.substring("minecraft/".length()));
            return;
        }
        Enumeration<CheckBoxTreeNode> e = node.children();
        for (; e.hasMoreElements();) {
            CheckBoxTreeNode n = e.nextElement();
            process(n, basePath + "/" + n.getUserObject(), list);
        }
    }

    public ModpackWizard(IMinecraftService service) {
        super(new WizardPanelProvider(C.i18n("modpack.wizard"), new String[] { C.i18n("modpack.wizard.step.1"), C.i18n("modpack.wizard.step.2") }, new String[] { C.i18n("modpack.wizard.step.1.title"), C.i18n("modpack.wizard.step.2.title") }) {

            @Override
            protected Object finish(Map settings) throws WizardException {
                return new DeferredWizardResult(false) {
                    @Override
                    public void start(Map settings, ResultProgressHandle progress) {
                        progress.setBusy("Processing modpack");
                        ArrayList<String> blackList = new ArrayList<>(ModpackManager.MODPACK_BLACK_LIST);
                        CheckBoxTreeNode root = (CheckBoxTreeNode) settings.get("blackList");
                        process(root, "minecraft", blackList);
                        try {
                            File loc = new File((String) settings.get(ModpackInitializationPanel.KEY_MODPACK_LOCATION));
                            ModpackManager.export(loc,
                                                  service.version(),
                                                  (String) settings.get(ModpackInitializationPanel.KEY_GAME_VERSION),
                                                  blackList);
                            progress.finished(new Summary(C.i18n("modpack.export_finished") + ": " + loc.getAbsolutePath(), null));
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
                    controller.setForwardNavigationMode(WizardController.MODE_CAN_FINISH);

                    return new ModpackFileSelectionPanel(controller, settings, service.baseDirectory(), ModpackManager.MODPACK_PREDICATE);
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
