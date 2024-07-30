package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.*;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public class AccountPage extends StackPane {

    public AccountPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        {
            VBox accountSettings = new VBox(8);
            accountSettings.getStyleClass().add("card-non-transparent");
            {
                VBox chooseWrapper = new VBox();
                chooseWrapper.setPadding(new Insets(8, 0, 8, 0));
                JFXCheckBox chkAutoCopyLoginCode = new JFXCheckBox(i18n("settings.account.microsoft.auto_copy_code"));
                chkAutoCopyLoginCode.selectedProperty().bindBidirectional(config().autoCopyCodeWhenLoginWithMicrosoftProperty());
                chooseWrapper.getChildren().setAll(chkAutoCopyLoginCode);

                accountSettings.getChildren().setAll(chooseWrapper);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("account.login")), accountSettings);
        }



    }

}
