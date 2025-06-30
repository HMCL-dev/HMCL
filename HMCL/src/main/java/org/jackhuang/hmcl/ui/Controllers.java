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
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorController;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.ConfigHolder.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Controllers {
    public static final String JAVA_VERSION_TIP = "javaVersion";

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
    private static Lazy<VersionPage> versionPage = new Lazy<>(VersionPage::new);
    private static Lazy<GameListPage> gameListPage = new Lazy<>(() -> {
        GameListPage gameListPage = new GameListPage();
        gameListPage.selectedProfileProperty().bindBidirectional(Profiles.selectedProfileProperty());
        gameListPage.profilesProperty().bindContent(Profiles.profilesProperty());
        FXUtils.applyDragListener(gameListPage, ModpackHelper::isFileModpackByExtension, modpacks -> {
            File modpack = modpacks.get(0);
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack), i18n("install.modpack"));
        });
        return gameListPage;
    });
    private static Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    private static DecoratorController decorator;
    private static Lazy<DownloadPage> downloadPage = new Lazy<>(DownloadPage::new);
    private static Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
        accountListPage.authServersProperty().bindContentBidirectional(config().getAuthlibInjectorServers());
        return accountListPage;
    });
    private static Lazy<LauncherSettingsPage> settingsPage = new Lazy<>(LauncherSettingsPage::new);

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
        return versionPage.get();
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
        return settingsPage.get();
    }

    // FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    // FXThread
    public static DownloadPage getDownloadPage() {
        return downloadPage.get();
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
                    && !Controllers.stage.isIconified()) {
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
                            new KeyValue(decorator.getDecorator().opacityProperty(), 0, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleXProperty(), 0.8, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleYProperty(), 0.8, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleZProperty(), 0.8, FXUtils.EASE)
                    ),
                    new KeyFrame(Duration.millis(600),
                            new KeyValue(decorator.getDecorator().opacityProperty(), 1, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleXProperty(), 1, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleYProperty(), 1, FXUtils.EASE),
                            new KeyValue(decorator.getDecorator().scaleZProperty(), 1, FXUtils.EASE)
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

        if (JavaRuntime.CURRENT_VERSION < 10) {
            Number shownTipVersion = null;

            try {
                shownTipVersion = (Number) config().getShownTips().get(JAVA_VERSION_TIP);
            } catch (ClassCastException e) {
                LOG.warning("Invalid type for shown tips key: " + JAVA_VERSION_TIP, e);
            }

            if (shownTipVersion == null || shownTipVersion.intValue() < 11) {
                String downloadLink = null;

                if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW)
                    downloadLink = "https://www.loongnix.cn/zh/api/java/downloads-jdk21/index.html";
                else {

                    EnumSet<Architecture> supportedArchitectures;
                    if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                        supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.X86, Architecture.ARM64);
                    else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                        supportedArchitectures = EnumSet.of(
                                Architecture.X86_64, Architecture.X86,
                                Architecture.ARM64, Architecture.ARM32,
                                Architecture.RISCV64, Architecture.LOONGARCH64
                        );
                    else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                        supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.ARM64);
                    else
                        supportedArchitectures = EnumSet.noneOf(Architecture.class);

                    if (supportedArchitectures.contains(Architecture.SYSTEM_ARCH))
                        downloadLink = String.format("https://docs.hmcl.net/downloads/%s/%s.html",
                                OperatingSystem.CURRENT_OS.getCheckedName(),
                                Architecture.SYSTEM_ARCH.getCheckedName()
                        );
                }

                MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("fatal.deprecated_java_version"), null, MessageType.WARNING);
                if (downloadLink != null)
                    builder.addHyperLink(i18n("fatal.deprecated_java_version.download_link", 21), downloadLink);
                Controllers.dialog(builder
                        .ok(() -> config().getShownTips().put(JAVA_VERSION_TIP, 11))
                        .build());
            }
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

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    public static CompletableFuture<String> prompt(String title, FutureCallback<String> onResult, String initialValue) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult);
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
        decorator.navigate(node);
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
        decorator = null;
        stage = null;
        scene = null;
        onApplicationStop();

        FXUtils.shutdown();
    }
}
