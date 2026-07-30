/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.account.skin;

import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.TextureType;
import org.jackhuang.hmcl.ui.construct.HintPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AuthlibInjectorAccoubtSkinPage extends SkinPageBase<AuthlibInjectorAccount> {
    private ReadOnlyObjectWrapper<Skin> skinProperty;

    public AuthlibInjectorAccoubtSkinPage(AuthlibInjectorAccount account) {
        super(account);

        if (!canUploadSkin()) {
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            hintPane.setSegment(i18n("account.skin.yggdrasil.unsupported", Optional.of(account.getServer().getLinks().get("homepage")).orElse(account.getServer().getUrl())));
            skinManagePane.leftRegion.getChildren().setAll(hintPane);
        }
    }

    @Override
    protected void onDrag(Path skin) {
        if (!canUploadSkin()) return;
        throw new UnsupportedOperationException();
    }

    @Override
    protected ReadOnlyObjectProperty<Skin> skinObjectProperty() {
        if (skinProperty == null) skinProperty = new ReadOnlyObjectWrapper<>();
        return skinProperty.getReadOnlyProperty();
    }

    public boolean canUploadSkin() {
        ObjectBinding<Optional<CompleteGameProfile>> profile = account.getYggdrasilService().getProfileRepository().binding(account.getProfileID());

        Set<TextureType> uploadableTextures = profile.get()
                .map(AuthlibInjectorAccount::getUploadableTextures)
                .orElse(emptySet());
        return uploadableTextures.contains(TextureType.SKIN);
    }
}
