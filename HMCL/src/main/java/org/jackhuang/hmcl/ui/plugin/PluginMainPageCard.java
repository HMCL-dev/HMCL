package org.jackhuang.hmcl.ui.plugin;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.plugin.PluginManager;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.plugin.api.PluginMainPageDesigner;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PluginMainPageCard extends VBox {
    private final PluginInfo pluginInfo;

    private static final String titlePrefix = i18n("plugin").toUpperCase(Locale.ROOT).charAt(0) + i18n("plugin").substring(1) + ": ";

    public PluginMainPageCard(PluginInfo pluginInfo) {
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("plugincard");
        this.setSpacing(8);
        this.pluginInfo = pluginInfo;
    }

    public void accept(PluginMainPageDesigner pluginMainPageDesigner) {
        FXUtils.runInFX(() -> {
            Label titleLabel = new Label(titlePrefix + pluginInfo.getPluginName());
            titleLabel.getStyleClass().add("plugincard-title");
            this.getChildren().setAll(titleLabel, new Separator(Orientation.HORIZONTAL));

            HBox currentLineWidgets = newLine();

            for (PluginMainPageDesigner.IPluginWidget widget : pluginMainPageDesigner.getWidgets()) {
                if (widget instanceof PluginMainPageDesigner.PluginButtonWidget) {
                    JFXButton jfxButton = new JFXButton(((PluginMainPageDesigner.PluginButtonWidget) widget).getText());
                    jfxButton.setOnMouseClicked((mouseEvent) -> {
                        PluginManager.sendEvent((pluginInfo, eventHandler) -> {
                            ((PluginMainPageDesigner.PluginButtonWidget) widget).onClick();
                        }, this.pluginInfo);
                    });
                    jfxButton.getStyleClass().add("plugincard-button");
                    currentLineWidgets.getChildren().add(jfxButton);
                } else if (widget instanceof PluginMainPageDesigner.PluginTextWidget) {
                    Label label = new Label(((PluginMainPageDesigner.PluginTextWidget) widget).getText());
                    currentLineWidgets.getChildren().add(label);
                } else if (widget instanceof PluginMainPageDesigner.PluginLinebreakWidget) {
                    this.getChildren().add(currentLineWidgets);
                    currentLineWidgets = newLine();
                } else if (widget instanceof PluginMainPageDesigner.PluginHorizontalSeparatorWidget) {
                    this.getChildren().addAll(currentLineWidgets, new Separator(Orientation.HORIZONTAL));
                    currentLineWidgets = newLine();
                } else if (widget instanceof PluginMainPageDesigner.PluginVerticalSeparatorWidget) {
                    currentLineWidgets.getChildren().add(new Separator(Orientation.VERTICAL));
                } else {
                    PluginManager.submitPluginException(pluginInfo, new NoClassDefFoundError(String.format("Unknown PluginMainPageDesignerWidget (Class \"%s\") found.", widget.getClass())));
                }
            }

            this.getChildren().add(currentLineWidgets);
        });
    }

    private static HBox newLine() {
        HBox hBox = new HBox(8);
        hBox.setAlignment(Pos.BASELINE_LEFT);
        return hBox;
    }
}
