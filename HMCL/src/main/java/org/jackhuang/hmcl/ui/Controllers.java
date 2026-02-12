/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.terracotta.TerracottaPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Controllers {
    public static final String JAVA_VERSION_TIP = "javaVersion";
    public static final String JAVA_INTERPRETED_MODE_TIP = "javaInterpretedMode";
    public static final String SOFTWARE_RENDERING = "softwareRendering";

    public static final int MIN_WIDTH = 800 + 2 + 16; // bg width + border width*2 + shadow width*2
    public static final int MIN_HEIGHT = 450 + 2 + 40 + 16; // bg height + border width*2 + toolbar height + shadow width*2
    public static final Screen SCREEN = Screen.getPrimary();
    private static InvalidationListener stageSizeChangeListener;
    private static DoubleProperty stageX = new SimpleDoubleProperty();
    private static DoubleProperty stageY = new SimpleDoubleProperty();
    private static DoubleProperty stageWidth = new SimpleDoubleProperty();
    private static DoubleProperty stageHeight = new SimpleDoubleProperty();

    private static Scene scene;
    private static Stage stage;
    private static VersionPage versionPage;
    private static Lazy<GameListPage> gameListPage = new Lazy<>(() -> {
        GameListPage gameListPage = new GameListPage();
        gameListPage.selectedProfileProperty().bindBidirectional(Profiles.selectedProfileProperty());
        gameListPage.profilesProperty().bindContent(Profiles.profilesProperty());
        FXUtils.applyDragListener(gameListPage, ModpackHelper::isFileModpackByExtension, modpacks -> {
            Path modpack = modpacks.get(0);
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack), i18n("install.modpack"));
        });
        return gameListPage;
    });
    private static Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    private static DecoratorController decorator;
    private static DownloadPage downloadPage;
    private static Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
        accountListPage.authServersProperty().bindContentBidirectional(config().getAuthlibInjectorServers());
        return accountListPage;
    });
    private static LauncherSettingsPage settingsPage;
    private static Lazy<TerracottaPage> terracottaPage = new Lazy<>(TerracottaPage::new);

    private Controllers() {
    }

    public static Scene getScene() {
        return scene;
    }

    public static Stage getStage() {
        return stage;
    }

    // FXThread
    public static VersionPage getVersionPage() {
        if (versionPage == null) {
            versionPage = new VersionPage();
        }
        return versionPage;
    }

    @FXThread
    public static void prepareVersionPage() {
        if (versionPage == null) {
            LOG.info("Prepare the version page");
            versionPage = FXUtils.prepareNode(new VersionPage());
        }
    }

    // FXThread
    public static GameListPage getGameListPage() {
        return gameListPage.get();
    }

    // FXThread
    public static RootPage getRootPage() {
        return rootPage.get();
    }

    // FXThread
    public static LauncherSettingsPage getSettingsPage() {
        if (settingsPage == null) {
            settingsPage = new LauncherSettingsPage();
        }
        return settingsPage;
    }

    @FXThread
    public static void prepareSettingsPage() {
        if (settingsPage == null) {
            LOG.info("Prepare the settings page");
            settingsPage = FXUtils.prepareNode(new LauncherSettingsPage());
        }
    }

    // FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    // FXThread
    public static DownloadPage getDownloadPage() {
        if (downloadPage == null) {
            downloadPage = new DownloadPage();
        }
        return downloadPage;
    }

    @FXThread
    public static void prepareDownloadPage() {
        if (downloadPage == null) {
            LOG.info("Prepare the download page");
            downloadPage = FXUtils.prepareNode(new DownloadPage());
        }
    }

    // FXThread
    public static Node getTerracottaPage() {
        return terracottaPage.get();
    }

    // FXThread
    public static DecoratorController getDecorator() {
        return decorator;
    }

    public static void onApplicationStop() {
        stageSizeChangeListener = null;
        if (stageX != null) {
            config().setX(stageX.get() / SCREEN.getBounds().getWidth());
            stageX = null;
        }
        if (stageY != null) {
            config().setY(stageY.get() / SCREEN.getBounds().getHeight());
            stageY = null;
        }
        if (stageHeight != null) {
            config().setHeight(stageHeight.get());
            stageHeight = null;
        }
        if (stageWidth != null) {
            config().setWidth(stageWidth.get());
            stageWidth = null;
        }
    }

    public static void initialize(Stage stage) {
        LOG.info("Start initializing application");

        if (System.getProperty("prism.lcdtext") == null) {
            String fontAntiAliasing = globalConfig().getFontAntiAliasing();
            if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                LOG.info("Enable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "true");
            } else if ("gray".equalsIgnoreCase(fontAntiAliasing)
                    || OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && SCREEN.getOutputScaleX() > 1) {
                LOG.info("Disable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "false");
            }
        }

        Controllers.stage = stage;

        stageSizeChangeListener = o -> {
            ReadOnlyDoubleProperty sourceProperty = (ReadOnlyDoubleProperty) o;
            DoubleProperty targetProperty;
            switch (sourceProperty.getName()) {
                case "x": {
                    targetProperty = stageX;
                    break;
                }
                case "y": {
                    targetProperty = stageY;
                    break;
                }
                case "width": {
                    targetProperty = stageWidth;
                    break;
                }
                case "height": {
                    targetProperty = stageHeight;
                    break;
                }
                default: {
                    targetProperty = null;
                }
            }

            if (targetProperty != null
                    && Controllers.stage != null
                    && !Controllers.stage.isIconified()
                    // https://github.com/HMCL-dev/HMCL/issues/4290
                    && (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS ||
                    !Controllers.stage.isFullScreen() && !Controllers.stage.isMaximized())
            ) {
                targetProperty.set(sourceProperty.get());
            }
        };

        WeakInvalidationListener weakListener = new WeakInvalidationListener(stageSizeChangeListener);

        double initWidth = Math.max(MIN_WIDTH, config().getWidth());
        double initHeight = Math.max(MIN_HEIGHT, config().getHeight());

        {
            double initX = config().getX() * SCREEN.getBounds().getWidth();
            double initY = config().getY() * SCREEN.getBounds().getHeight();

            boolean invalid = true;
            double border = 20D;
            for (Screen screen : Screen.getScreens()) {
                Rectangle2D bound = screen.getBounds();

                if (bound.getMinX() + border <= initX + initWidth && initX <= bound.getMaxX() - border && bound.getMinY() + border <= initY && initY <= bound.getMaxY() - border) {
                    invalid = false;
                    break;
                }
            }

            if (invalid) {
                initX = (0.5D - initWidth / SCREEN.getBounds().getWidth() / 2) * SCREEN.getBounds().getWidth();
                initY = (0.5D - initHeight / SCREEN.getBounds().getHeight() / 2) * SCREEN.getBounds().getHeight();
            }

            stage.setX(initX);
            stage.setY(initY);
            stageX.set(initX);
            stageY.set(initY);
        }

        stage.setHeight(initHeight);
        stage.setWidth(initWidth);
        stageHeight.set(initHeight);
        stageWidth.set(initWidth);
        stage.xProperty().addListener(weakListener);
        stage.yProperty().addListener(weakListener);
        stage.heightProperty().addListener(weakListener);
        stage.widthProperty().addListener(weakListener);

        stage.setOnCloseRequest(e -> Launcher.stopApplication());

        decorator = new DecoratorController(stage, getRootPage());

        if (config().getCommonDirType() == EnumCommonDirectory.CUSTOM &&
                !FileUtils.canCreateDirectory(config().getCommonDirectory())) {
            config().setCommonDirType(EnumCommonDirectory.DEFAULT);
            dialog(i18n("launcher.cache_directory.invalid"));
        }

        Lang.thread(JavaManager::initialize, "Search Java", true);

        scene = new Scene(decorator.getDecorator());
        scene.setFill(Color.TRANSPARENT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        decorator.getDecorator().prefWidthProperty().bind(scene.widthProperty());
        decorator.getDecorator().prefHeightProperty().bind(scene.heightProperty());
        StyleSheets.init(scene);

        FXUtils.setIcon(stage);
        stage.setTitle(Metadata.FULL_TITLE);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);

        if (AnimationUtils.playWindowAnimation()) {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(0),
                            new KeyValue(decorator.getDecorator().opacityProperty(), 0, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleXProperty(), 0.8, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleYProperty(), 0.8, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleZProperty(), 0.8, Motion.EASE)
                    ),
                    new KeyFrame(Duration.millis(600),
                            new KeyValue(decorator.getDecorator().opacityProperty(), 1, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(decorator.getDecorator().scaleZProperty(), 1, Motion.EASE)
                    )
            );
            timeline.play();
        }

        if (!Architecture.SYSTEM_ARCH.isX86() && globalConfig().getPlatformPromptVersion() < 1) {
            Runnable continueAction = () -> globalConfig().setPlatformPromptVersion(1);

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                Controllers.dialog(i18n("fatal.unsupported_platform.macos_arm64"), null, MessageType.INFO, continueAction);
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                Controllers.dialog(i18n("fatal.unsupported_platform.windows_arm64"), null, MessageType.INFO, continueAction);
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX &&
                    (Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64
                            || Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW
                            || Architecture.SYSTEM_ARCH == Architecture.MIPS64EL)) {
                Controllers.dialog(i18n("fatal.unsupported_platform.loongarch"), null, MessageType.INFO, continueAction);
            } else {
                Controllers.dialog(i18n("fatal.unsupported_platform"), null, MessageType.WARNING, continueAction);
            }
        }

        if (JavaRuntime.CURRENT_VERSION < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
            Number shownTipVersion = null;
            try {
                shownTipVersion = (Number) config().getShownTips().get(JAVA_VERSION_TIP);
            } catch (ClassCastException e) {
                LOG.warning("Invalid type for shown tips key: " + JAVA_VERSION_TIP, e);
            }
            if (shownTipVersion == null || shownTipVersion.intValue() < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
                MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("fatal.deprecated_java_version"), null, MessageType.WARNING);
                String downloadLink = Metadata.getSuggestedJavaDownloadLink();
                if (downloadLink != null)
                    builder.addHyperLink(
                            i18n("fatal.deprecated_java_version.download_link", Metadata.RECOMMENDED_JAVA_VERSION),
                            downloadLink
                    );
                Controllers.dialog(builder
                        .ok(() -> config().getShownTips().put(JAVA_VERSION_TIP, Metadata.MINIMUM_SUPPORTED_JAVA_VERSION))
                        .build());
            }
        }

        // Check whether JIT is enabled in the current environment
        if (!JavaRuntime.CURRENT_JIT_ENABLED && !Boolean.TRUE.equals(config().getShownTips().get(JAVA_INTERPRETED_MODE_TIP))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.java_interpreted_mode"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            config().getShownTips().put(JAVA_INTERPRETED_MODE_TIP, true))
                    .build());
        }

        // Check whether hardware acceleration is enabled
        if (!FXUtils.GPU_ACCELERATION_ENABLED && !Boolean.TRUE.equals(config().getShownTips().get(SOFTWARE_RENDERING))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.software_rendering"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            config().getShownTips().put(SOFTWARE_RENDERING, true))
                    .build());
        }

        if (globalConfig().getAgreementVersion() < 1) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("launcher.agreement.hint")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setExternalLink(Metadata.EULA_URL);
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                globalConfig().setAgreementVersion(1);
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> javafx.application.Platform.exit());
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        }
    }

    public static void dialog(Region content) {
        if (decorator != null)
            decorator.showDialog(content);
    }

    public static void dialog(String text) {
        dialog(text, null);
    }

    public static void dialog(String text, String title) {
        dialog(text, title, MessageType.INFO);
    }

    public static void dialog(String text, String title, MessageType type) {
        dialog(text, title, type, null);
    }

    public static void dialog(String text, String title, MessageType type, Runnable ok) {
        dialog(new MessageDialogPane.Builder(text, title, type).ok(ok).build());
    }

    public static void confirm(String text, String title, Runnable yes, Runnable no) {
        confirm(text, title, MessageType.QUESTION, yes, no);
    }

    public static void confirm(String text, String title, MessageType type, Runnable yes, Runnable no) {
        dialog(new MessageDialogPane.Builder(text, title, type).yesOrNo(yes, no).build());
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, null).build());
    }

    public static void confirmAction(String text, String title, MessageType type, ButtonBase actionButton, Runnable cancel) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, cancel).build());
    }

    public static void confirmWithCountdown(String text, String title, int seconds, MessageType messageType,
                                            @Nullable Runnable ok, @Nullable Runnable cancel) {
        if (seconds <= 0)
            throw new IllegalArgumentException("Seconds must be greater than 0");

        JFXButton btnOk = new JFXButton(i18n("button.ok"));
        btnOk.getStyleClass().add(messageType == MessageType.WARNING || messageType == MessageType.ERROR
                ? "dialog-error"
                : "dialog-accept");

        if (ok != null)
            btnOk.setOnAction(e -> ok.run());
        btnOk.setDisable(true);

        KeyFrame[] keyFrames = new KeyFrame[seconds + 1];
        for (int i = 0; i < seconds; i++) {
            keyFrames[i] = new KeyFrame(Duration.seconds(i),
                    new KeyValue(btnOk.textProperty(), i18n("button.ok.countdown", seconds - i)));
        }
        keyFrames[seconds] = new KeyFrame(Duration.seconds(seconds),
                new KeyValue(btnOk.textProperty(), i18n("button.ok")),
                new KeyValue(btnOk.disableProperty(), false));

        Timeline timeline = new Timeline(keyFrames);
        confirmAction(text, title, messageType, btnOk, () -> {
            timeline.stop();
            if (cancel != null)
                cancel.run();
        });
        timeline.play();
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult, String initialValue, ValidatorBase... validators) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult, validators);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static CompletableFuture<List<PromptDialogPane.Builder.Question<?>>> prompt(PromptDialogPane.Builder builder) {
        PromptDialogPane pane = new PromptDialogPane(builder);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, String title, TaskCancellationAction onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    public static TaskExecutorDialogPane taskDialog(Task<?> task, String title, TaskCancellationAction onCancel) {
        TaskExecutor executor = task.executor();
        TaskExecutorDialogPane pane = taskDialog(executor, title, onCancel);
        executor.start();
        return pane;
    }

    public static void navigate(Node node) {
        decorator.navigate(node, ContainerAnimations.NAVIGATION, Motion.SHORT4, Motion.EASE);
    }

    public static void navigateForward(Node node) {
        decorator.navigate(node, ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    public static void showToast(String content) {
        decorator.showToast(content);
    }

    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            switch (href) {
                case "hmcl://settings/feedback":
                    Controllers.getSettingsPage().showFeedback();
                    Controllers.navigate(Controllers.getSettingsPage());
                    break;
                case "hmcl://game/launch":
                    Profile profile = Profiles.getSelectedProfile();
                    Versions.launch(profile, profile.getSelectedVersion(), LauncherHelper::setKeep);
                    break;
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    public static boolean isStopped() {
        return decorator == null;
    }

    public static void shutdown() {
        rootPage = null;
        versionPage = null;
        gameListPage = null;
        downloadPage = null;
        accountListPage = null;
        settingsPage = null;
        terracottaPage = null;
        decorator = null;
        stage = null;
        scene = null;
        onApplicationStop();

        FXUtils.shutdown();
    }
}
