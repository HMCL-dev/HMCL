package org.jackhuang.hellominecraft.launcher.utils.auth;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.DigestUtils;

/**
 *
 * @author hyh
 */
public final class OfflineAuthenticator extends IAuthenticator {

    public OfflineAuthenticator(String clientToken) {
        super(clientToken);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) {
        UserProfileProvider result = new UserProfileProvider();
        result.setSuccess(StrUtils.isNotBlank(info.username));
        result.setUserName(info.username);
        String md5 = DigestUtils.md5Hex(info.username);
        String uuid = md5.substring(0, 8) + '-' + md5.substring(8, 12) + '-' + md5.substring(12, 16) + '-' + md5.substring(16, 21) + md5.substring(21);
        result.setSession(uuid);
        result.setUserId(uuid);
        result.setAccessToken("${auth_access_token}");
        result.setUserType("Legacy");
        result.setErrorReason(C.i18n("login.no_Player007"));
        return result;
    }

    @Override
    public String getName() {
        return C.i18n("login.methods.offline");
    }

    @Override
    public boolean isHidePasswordBox() {
        return true;
    }

    @Override
    public UserProfileProvider loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
    }

}
