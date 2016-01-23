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
package org.jackhuang.hellominecraft.launcher.views.modpack;

import java.util.Iterator;
import java.util.Map;
import javax.swing.JComponent;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.views.wizard.spi.WizardBranchController;
import org.jackhuang.hellominecraft.utils.views.wizard.spi.WizardController;
import org.jackhuang.hellominecraft.utils.views.wizard.spi.WizardPanelProvider;

/**
 *
 * @author huangyuhui
 */
public class ModpackWizard extends WizardBranchController {

    public ModpackWizard(IMinecraftProvider provider) {
        super(new WizardPanelProvider("Modpack Wizard", new String[] { "Settings" }, new String[] { "Select location, version and allow version" }) {

            @Override
            protected JComponent createPanel(WizardController controller, String id, Map settings) {
                switch (indexOfStep(id)) {
                case 0:
                    String[] s = new String[provider.getVersionCount()];
                    Iterator<MinecraftVersion> it = provider.getVersions().iterator();
                    for (int i = 0; i < s.length; i++)
                        s[i] = it.next().id;
                    return new ModpackInitializationPanel(controller, settings, s);
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
