package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXRadioButton;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.platform.Platform;

import java.util.List;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaDownloadDialog extends DialogPane {
    private final DownloadProvider downloadProvider;
    private final List<GameJavaVersion> versions;
    private final ToggleGroup toggleGroup = new ToggleGroup();

    public JavaDownloadDialog(DownloadProvider downloadProvider, List<GameJavaVersion> versions) {
        this.downloadProvider = downloadProvider;
        this.versions = versions;

        setTitle(i18n("java.download"));
        validProperty().bind(toggleGroup.selectedToggleProperty().isNotNull());

        VBox vbox = new VBox(16);
        Label prompt = new Label(i18n("java.download.prompt"));
        vbox.getChildren().add(prompt);

        for (GameJavaVersion version : versions) {
            JFXRadioButton button = new JFXRadioButton("Java " + version.getMajorVersion());
            button.setUserData(version);

            try {
                if (JavaManager.isInstalled(version)) {
                    button.setDisable(true);
                }
            } catch (InterruptedException ignored) {
            }

            vbox.getChildren().add(button);
            toggleGroup.getToggles().add(button);
        }

        setBody(vbox);
    }

    @Override
    protected void onAccept() {
        fireEvent(new DialogCloseEvent());

        GameJavaVersion javaVersion = (GameJavaVersion) toggleGroup.getSelectedToggle().getUserData();
        if (javaVersion != null) {
            Controllers.taskDialog(JavaManager.installJava(downloadProvider, Platform.SYSTEM_PLATFORM, javaVersion).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception != null) {
                    Throwable resolvedException = resolveException(exception);
                    LOG.warning("Failed to download java", exception);
                    if (!(resolvedException instanceof CancellationException)) {
                        Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                    }
                }
            }), i18n("download.java"), TaskCancellationAction.NORMAL);
        }
    }
}
