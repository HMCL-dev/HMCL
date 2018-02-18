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
package org.jackhuang.hmcl.game;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.ProfileTexture;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Scheduler;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.DialogController;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.net.Proxy;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public final class AccountHelper {
    public static final AccountHelper INSTANCE = new AccountHelper();
    private AccountHelper() {}

    public static final File SKIN_DIR = new File(Main.HMCL_DIRECTORY, "skins");

    public static void loadSkins() {
        loadSkins(Proxy.NO_PROXY);
    }

    public static void loadSkins(Proxy proxy) {
        for (Account account : Settings.INSTANCE.getAccounts()) {
            if (account instanceof YggdrasilAccount) {
                new SkinLoadTask((YggdrasilAccount) account, proxy, false).start();
            }
        }
    }

    public static Task loadSkinAsync(YggdrasilAccount account) {
        return loadSkinAsync(account, Settings.INSTANCE.getProxy());
    }

    public static Task loadSkinAsync(YggdrasilAccount account, Proxy proxy) {
        return new SkinLoadTask(account, proxy, false);
    }

    public static Task refreshSkinAsync(YggdrasilAccount account) {
        return refreshSkinAsync(account, Settings.INSTANCE.getProxy());
    }

    public static Task refreshSkinAsync(YggdrasilAccount account, Proxy proxy) {
        return new SkinLoadTask(account, proxy, true);
    }

    private static File getSkinFile(String name) {
        return new File(SKIN_DIR, name + ".png");
    }

    public static Image getSkin(YggdrasilAccount account) {
        return getSkin(account, 1);
    }

    public static Image getSkin(YggdrasilAccount account, double scaleRatio) {
        if (account.getSelectedProfile() == null)
            return getDefaultSkin(account, scaleRatio);
        String name = account.getSelectedProfile().getName();
        if (name == null)
            return getDefaultSkin(account, scaleRatio);
        File file = getSkinFile(name);
        if (file.exists()) {
            Image original = new Image("file:" + file.getAbsolutePath());
            return new Image("file:" + file.getAbsolutePath(),
                    original.getWidth() * scaleRatio,
                    original.getHeight() * scaleRatio,
                    false, false);
        }
        return getDefaultSkin(account, scaleRatio);
    }

    public static Image getSkinImmediately(YggdrasilAccount account, GameProfile profile, double scaleRatio, Proxy proxy) throws Exception {
        String name = profile.getName();
        File file = getSkinFile(name);
        downloadSkin(account, profile, true, proxy);
        if (!file.exists())
            return getDefaultSkin(account, scaleRatio);

        String url = "file:" + file.getAbsolutePath();
        return scale(url, scaleRatio);
    }

    public static Rectangle2D getViewport(double scaleRatio) {
        double size = 8.0 * scaleRatio;
        return new Rectangle2D(size, size, size, size);
    }

    private static class SkinLoadTask extends Task {
        private final YggdrasilAccount account;
        private final Proxy proxy;
        private final boolean refresh;
        private final List<Task> dependencies = new LinkedList<>();

        public SkinLoadTask(YggdrasilAccount account, Proxy proxy) {
            this(account, proxy, false);
        }

        public SkinLoadTask(YggdrasilAccount account, Proxy proxy, boolean refresh) {
            this.account = account;
            this.proxy = proxy;
            this.refresh = refresh;
        }

        @Override
        public Scheduler getScheduler() {
            return Schedulers.io();
        }

        @Override
        public Collection<Task> getDependencies() {
            return dependencies;
        }

        @Override
        public void execute() throws Exception {
            if (account.canLogIn() && (account.getSelectedProfile() == null || refresh))
                DialogController.logIn(account);

            downloadSkin(account, account.getSelectedProfile(), refresh, proxy);
        }
    }

    private static void downloadSkin(YggdrasilAccount account, GameProfile profile, boolean refresh, Proxy proxy) throws Exception {
        if (profile == null) return;
        String name = profile.getName();
        if (name == null) return;
        Optional<ProfileTexture> texture = account.getSkin(profile);
        if (!texture.isPresent()) return;
        String url = texture.get().getUrl();
        File file = getSkinFile(name);
        if (!refresh && file.exists())
            return;
        new FileDownloadTask(NetworkUtils.toURL(url), file, proxy).run();
    }

    public static Image scale(String url, double scaleRatio) {
        Image origin = new Image(url);
        return new Image(url,
                origin.getWidth() * scaleRatio,
                origin.getHeight() * scaleRatio,
                false, false);
    }

    public static Image getSteveSkin(double scaleRatio) {
         return scale("/assets/img/steve.png", 4);
    }

    public static Image getAlexSkin(double scaleRatio) {
        return scale("/assets/img/alex.png", 4);
    }

    public static Image getDefaultSkin(Account account, double scaleRatio) {
        if (account == null)
            return getSteveSkin(scaleRatio);

        int type = account.getUUID().hashCode() & 1;
        if (type == 1)
            return getAlexSkin(scaleRatio);
        else
            return getSteveSkin(scaleRatio);
    }
}
