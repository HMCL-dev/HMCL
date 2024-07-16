package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class JavaItem extends Control {
    private final JavaRuntime java;

    public JavaItem(JavaRuntime java) {
        this.java = java;
    }

    public JavaRuntime getJava() {
        return java;
    }

    public void onReveal() {
        Path target;

        Path parent = java.getBinary().getParent();
        if (parent != null) {
            target = parent.getParent() != null ? parent.getParent() : parent;
        } else {
            target = java.getBinary();
        }

        FXUtils.showFileInExplorer(target);
    }

    public void onRemove() {
        if (java.isManaged()) {
            Controllers.taskDialog(JavaManager.uninstallJava(java), i18n("java.uninstall"), TaskCancellationAction.NORMAL);
        } else {
            String path = java.getBinary().toString();
            ConfigHolder.globalConfig().getUserJava().remove(path);
            ConfigHolder.globalConfig().getDisabledJava().add(path);
            try {
                JavaManager.removeJava(java);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JavaRuntimeItemSkin(this);
    }

    private static final class JavaRuntimeItemSkin extends SkinBase<JavaItem> {

        JavaRuntimeItemSkin(JavaItem skinnable) {
            super(skinnable);
            JavaRuntime java = skinnable.getJava();
            String vendor = JavaInfo.normalizeVendor(java.getVendor());

            BorderPane root = new BorderPane();

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            TwoLineListItem item = new TwoLineListItem();
            item.setTitle((java.isJDK() ? "JDK" : "JRE") + " " + java.getVersion());
            item.setSubtitle(java.getBinary().toString());
            item.getTags().add(i18n("java.info.architecture") + ": " + java.getArchitecture().getDisplayName());
            if (vendor != null)
                item.getTags().add(i18n("java.info.vendor") + ": " + vendor);
            BorderPane.setAlignment(item, Pos.CENTER);
            center.getChildren().setAll(item);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);
            {
                JFXButton revealButton = new JFXButton();
                revealButton.getStyleClass().add("toggle-icon4");
                revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));
                revealButton.setOnAction(e -> skinnable.onReveal());

                JFXButton removeButton = new JFXButton();
                removeButton.getStyleClass().add("toggle-icon4");
                removeButton.setOnAction(e -> Controllers.confirm(
                        java.isManaged() ? i18n("java.uninstall.confirm") : i18n("java.disable.confirm"),
                        i18n("message.warning"),
                        skinnable::onRemove,
                        null
                ));
                if (java.isManaged()) {
                    removeButton.setGraphic(FXUtils.limitingSize(SVG.DELETE_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));
                    FXUtils.installFastTooltip(removeButton, i18n("java.uninstall"));
                    if (JavaRuntime.CURRENT_JAVA != null && java.getBinary().equals(JavaRuntime.CURRENT_JAVA.getBinary()))
                        removeButton.setDisable(true);
                } else {
                    removeButton.setGraphic(FXUtils.limitingSize(SVG.CLOSE.createIcon(Theme.blackFill(), 24, 24), 24, 24));
                    FXUtils.installFastTooltip(removeButton, i18n("java.disable"));
                }

                right.getChildren().setAll(revealButton, removeButton);
            }
            root.setRight(right);

            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            getChildren().setAll(new RipplerContainer(root));
        }
    }
}
