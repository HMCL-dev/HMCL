/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.models;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jackhuang.hellominecraftlauncher.utilities.MinecraftOldVersionIncluder;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;

/**
 *
 * @author hyh
 */
public class VersionManager {

    /**
     * 导入旧版本
     * @param mcp
     * @return Controller Action
     */
    public ControllerAction<String> includeOldVersion() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择MINECRAFT路径"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(null);
        try {
            String mcp = fc.getSelectedFile().getCanonicalPath();
            if (mcp == null || mcp.trim().equals("")) {
                return new ControllerAction<String>(-1, "");
            }
            File file = new File(mcp + File.separator + "bin" + File.separator + "minecraft.jar");
            if (!file.exists()) {
                //MessageBox.Show(C.I18N.getString("找不到MINECRAFT.JAR"));
                return new ControllerAction<String>(6, "");
            }

            String name = JOptionPane.showInputDialog(C.I18N.getString("请输入该旧版本的名称"));
            file = new File(Utils.addSeparator(SettingsManager.settings.publicSettings.gameDir)
                    + "versions" + File.separator + name);
            if (file.exists()) {
                //MessageBox.Show(C.I18N.getString("找到同名的MINECRAFT版本，请换用另一个名称"));
                return new ControllerAction<String>(7, "");
            }

            MinecraftOldVersionIncluder l = new MinecraftOldVersionIncluder(mcp, name);
            l.include();

            return new ControllerAction<String>(4, "");
        } catch (Exception e) {
            //MessageBox.Show(
            //        java.text.MessageFormat.format(
            //        C.I18N.getString("导入失败: {0}"), new Object[]{e.getMessage()}));
            return new ControllerAction<String>(0, e.getMessage());
        }
    }
}
