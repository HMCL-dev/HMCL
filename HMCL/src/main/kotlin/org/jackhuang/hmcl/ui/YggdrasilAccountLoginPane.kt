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
package org.jackhuang.hmcl.ui

import com.jfoenix.controls.JFXDialog
import com.jfoenix.controls.JFXPasswordField
import com.jfoenix.controls.JFXProgressBar
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.auth.AuthInfo
import org.jackhuang.hmcl.auth.yggdrasil.InvalidCredentialsException
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory
import org.jackhuang.hmcl.game.HMCLMultiCharacterSelector
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.util.taskResult

class YggdrasilAccountLoginPane(private val oldAccount: YggdrasilAccount, private val success: (AuthInfo) -> Unit, private val failed: () -> Unit) : StackPane() {
    @FXML lateinit var lblUsername: Label
    @FXML lateinit var txtPassword: JFXPasswordField
    @FXML lateinit var lblCreationWarning: Label
    @FXML lateinit var progressBar: JFXProgressBar
    lateinit var dialog: JFXDialog

    init {
        loadFXML("/assets/fxml/yggdrasil-account-login.fxml")

        lblUsername.text = oldAccount.username
        txtPassword.setOnAction {
            onAccept()
        }
    }

    fun onAccept() {
        val username = oldAccount.username
        val password = txtPassword.text
        progressBar.isVisible = true
        lblCreationWarning.text = ""
        taskResult("login") {
            try {
                val account = YggdrasilAccountFactory.INSTANCE.fromUsername(username, password)
                account.logIn(HMCLMultiCharacterSelector.INSTANCE, Settings.INSTANCE.proxy)
            } catch (e: Exception) {
                e
            }
        }.subscribe(Schedulers.javafx()) {
            val account: Any = it["login"]
            if (account is AuthInfo) {
                success(account)
                dialog.close()
            } else if (account is InvalidCredentialsException) {
                lblCreationWarning.text = i18n("login.wrong_password")
            } else if (account is Exception) {
                lblCreationWarning.text = account.javaClass.toString() + ": " + account.localizedMessage
            }
            progressBar.isVisible = false
        }
    }

    fun onCancel() {
        failed()
        dialog.close()
    }
}