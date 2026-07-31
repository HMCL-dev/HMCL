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
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.terracotta.TerracottaPage;
import org.jackhuang.hmcl.ui.instances.GameListPage;
import org.jackhuang.hmcl.ui.instances.GameInstancePage;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.setting.SettingsManager.userState;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Controllers {
    public static final String JAVA_VERSION_TIP = "javaVersion";
    public static final String JAVA_INTERPRETED_MODE_TIP = "javaInterpretedMode";
    public static final String SOFTWARE_RENDERING = "softwareRendering";
    public static final String APRIL_FOOLS = "aprilFools";

    private static GameInstancePage gameInstancePage;
    private static Lazy<GameListPage> gameListPage = new Lazy<>(GameListPage::new);
    private static Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    /// The coordinator for the main window's scene graph and navigation stack.
    private static @Nullable Decorator decorator;
    private static DownloadPage downloadPage;
    private static Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
        accountListPage.authServersProperty().bindContentBidirectional(getAuthlibInjectorServers());
        return accountListPage;
    });
    private static LauncherSettingsPage settingsPage;
    private static Lazy<TerracottaPage> terracottaPage = new Lazy<>(TerracottaPage::new);

    private Controllers() {
    }

    /// Action used by confirmation dialogs that may fail before the confirmed operation is complete.
    @FunctionalInterface
    public interface ThrowingRunnable {
        /// Runs the confirmed action.
        ///
        /// @throws Exception if the action fails
        void run() throws Exception;
    }

    /// Returns the stage currently attached to the main-window decorator.
    ///
    /// @return the current stage, or `null` before initialization or after detachment
    public static @Nullable Stage getStage() {
        @Nullable Decorator currentDecorator = decorator;
        return currentDecorator == null ? null : currentDecorator.getStage();
    }

    @FXThread
    public static GameInstancePage getGameInstancePage() {
        if (gameInstancePage == null) {
            gameInstancePage = new GameInstancePage();
        }
        return gameInstancePage;
    }

    @FXThread
    public static void prepareGameInstancePage() {
        if (gameInstancePage == null) {
            LOG.info("Prepare the game instance page");
            gameInstancePage = FXUtils.prepareNode(new GameInstancePage());
        }
    }

    @FXThread
    public static GameListPage getGameListPage() {
        return gameListPage.get();
    }

    @FXThread
    public static RootPage getRootPage() {
        return rootPage.get();
    }

    @FXThread
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

    @FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    @FXThread
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

    @FXThread
    public static Node getTerracottaPage() {
        return terracottaPage.get();
    }

    /// Returns the initialized main-window decorator.
    ///
    /// @return the application-wide main-window decorator
    @FXThread
    public static Decorator getDecorator() {
        return Objects.requireNonNull(decorator, "Main window is not initialized");
    }

    /// Releases stage-specific listeners and retained window ownership before application shutdown.
    public static void onApplicationStop() {
        if (decorator != null) {
            decorator.detachStage();
        }
    }

    /// Initializes the main application stage, scene graph, and background services.
    ///
    /// @param stage the primary application stage, which must not have been shown
    public static void initialize(Stage stage) {
        LOG.info("Start initializing application");

        LOG.info("April Fools: " + AprilFools.isEnabled());

        if (System.getProperty("prism.lcdtext") == null) {
            @Nullable String fontAntiAliasing = SettingsManager.userSettings().fontAntiAliasingProperty().get();
            if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                LOG.info("Enable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "true");
            } else if ("gray".equalsIgnoreCase(fontAntiAliasing)
                    || OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Screen.getPrimary().getOutputScaleX() > 1) {
                LOG.info("Disable sub-pixel antialiasing");
                System.getProperties().put("prism.lcdtext", "false");
            }
        }

        stage.setOnCloseRequest(e -> Launcher.stopApplication());

        decorator = new Decorator(getRootPage());
        Scene mainScene = decorator.attachStage(stage);
        getRootPage().getMainPage().showUpdateProperty().bind(UpdateChecker.checkingUpdateProperty().not().and(UpdateChecker.outdatedProperty()));
        getRootPage().getMainPage().showUpdateDialogProperty().bind(
                decorator.backableProperty().not()
                        .and(getRootPage().getMainPage().showUpdateProperty())
                        .and(settings().disableAutoShowUpdateDialogProperty().not())
        );

        if (settings().commonDirectoryTypeProperty().get() == EnumCommonDirectory.CUSTOM &&
                !FileUtils.canCreateDirectory(settings().getResolvedCommonDirectory())) {
            settings().commonDirectoryTypeProperty().set(EnumCommonDirectory.DEFAULT);
            dialog(i18n("launcher.cache_directory.invalid"));
        }

        Lang.thread(JavaManager::initialize, "Search Java", true);

        StyleSheets.init(mainScene);

        FXUtils.setIcon(stage);
        stage.setTitle(Metadata.FULL_TITLE);

        if (!Architecture.SYSTEM_ARCH.isX86() && SettingsManager.userState().platformPromptVersionProperty().get() < 1) {
            Runnable continueAction = () -> {
                UserState userState = userState();
                userState.platformPromptVersionProperty().set(1);
            };

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                continueAction.run();
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
                shownTipVersion = (Number) state().getShownTips().get(JAVA_VERSION_TIP);
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
                        .ok(() -> state().getShownTips().put(JAVA_VERSION_TIP, Metadata.MINIMUM_SUPPORTED_JAVA_VERSION))
                        .build());
            }
        }

        // Check whether JIT is enabled in the current environment
        if (!JavaRuntime.CURRENT_JIT_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(JAVA_INTERPRETED_MODE_TIP))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.java_interpreted_mode"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(JAVA_INTERPRETED_MODE_TIP, true))
                    .build());
        }

        // Check whether hardware acceleration is enabled
        if (!FXUtils.GPU_ACCELERATION_ENABLED && !Boolean.TRUE.equals(state().getShownTips().get(SOFTWARE_RENDERING))) {
            Controllers.dialog(new MessageDialogPane.Builder(i18n("warning.software_rendering"), i18n("message.warning"), MessageType.WARNING)
                    .ok(null)
                    .addCancel(i18n("button.do_not_show_again"), () ->
                            state().getShownTips().put(SOFTWARE_RENDERING, true))
                    .build());
        }

        if (SettingsManager.userState().agreementVersionProperty().get() < 1) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("launcher.agreement.hint")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setExternalLink(Metadata.EULA_URL);
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                UserState userState = userState();
                userState.agreementVersionProperty().set(1);
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> javafx.application.Platform.exit());
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        }

        aprilFools:
        if (AprilFools.isEnabled()) {
            int currentYear = LocalDate.now().getYear();
            if (state().getShownTips().get(APRIL_FOOLS) instanceof Number year && year.intValue() >= currentYear)
                break aprilFools;

            if (!I18n.getLocale().getLocale().getLanguage().equals("zh"))
                break aprilFools;

            SupportedLocale lzh = SupportedLocale.getSupportedLocales().stream()
                    .filter(locale -> "lzh".equals(locale.getName()))
                    .findFirst().orElse(null);

            if (lzh == null) {
                LOG.warning("No supported locale found for lzh");
                break aprilFools;
            }

            Runnable updateShowTips = () -> state().getShownTips().put(APRIL_FOOLS, currentYear);

            Controllers.confirmWithCountdown(i18n("launcher.april_fools.switch_lzh"), null, 10,
                    MessageType.QUESTION, () -> {
                        Controllers.confirm(i18n("launcher.april_fools.switch_lzh.confirm"), null, MessageType.QUESTION, () -> {
                            LOG.info("Switching locale to " + lzh);

                            updateShowTips.run();
                            settings().languageProperty().set(lzh);

                            Controllers.onApplicationStop();

                            try {
                                FileSaver.waitForAllSaves();
                            } catch (InterruptedException ignored) {
                                // Ignore
                            }

                            try {
                                Restarter.restartSelf();
                            } catch (IOException e) {
                                LOG.warning("Failed to restart self", e);
                            }

                            Platform.exit();
                        }, updateShowTips);
                    }, updateShowTips);
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

    /// Shows a warning that confirms backing up a read-only settings file before overwriting it.
    ///
    /// @param text      the file-specific read-only warning
    /// @param overwrite the action that backs up and overwrites the file
    public static void confirmBackupAndOverwrite(String text, ThrowingRunnable overwrite) {
        dialog(new MessageDialogPane.Builder(
                text + "\n\n" + i18n("settings.file.force_write.confirm"),
                i18n("message.warning"),
                MessageType.WARNING)
                .addAction(i18n("settings.file.force_write"), () -> {
                    try {
                        overwrite.run();
                    } catch (Exception e) {
                        LOG.warning("Failed to force overwrite settings file", e);
                        dialog(i18n("message.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                i18n("message.error"),
                                MessageType.ERROR);
                    }
                })
                .addCancel(null)
                .build());
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

    public static void dialogLater(Region content) {
        if (decorator != null)
            decorator.showDialogLater(content);
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

    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, String title, @NotNull TaskCancellationAction onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    public static TaskExecutorDialogPane taskDialog(Task<?> task, String title, @NotNull TaskCancellationAction onCancel) {
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

    /// Shows `directoryChooser` with the current main window as its owner.
    ///
    /// @param directoryChooser the chooser to show
    /// @return the selected directory, or `null` if the chooser is cancelled
    public static @Nullable Path showDialog(DirectoryChooser directoryChooser) {
        return FileUtils.toPath(directoryChooser.showDialog(getStage()));
    }

    /// Shows `fileChooser` for opening one file with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected file, or `null` if the chooser is cancelled
    public static @Nullable Path showOpenDialog(FileChooser fileChooser) {
        return FileUtils.toPath(fileChooser.showOpenDialog(getStage()));
    }

    /// Shows `fileChooser` for saving one file with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected file, or `null` if the chooser is cancelled
    public static @Nullable Path showSaveDialog(FileChooser fileChooser) {
        return FileUtils.toPath(fileChooser.showSaveDialog(getStage()));
    }

    /// Shows `fileChooser` for opening multiple files with the current main window as its owner.
    ///
    /// @param fileChooser the chooser to show
    /// @return the selected files, or `null` if the chooser is cancelled
    public static @Nullable List<Path> showOpenMultipleDialog(FileChooser fileChooser) {
        return FileUtils.toPaths(fileChooser.showOpenMultipleDialog(getStage()));
    }

    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            switch (href) {
                case "hmcl://settings/feedback":
                    Controllers.getSettingsPage().showFeedback();
                    Controllers.navigate(Controllers.getSettingsPage());
                    break;
                case "hmcl://game/launch":
                    var repository = GameDirectoryManager.getSelectedRepository();
                    Instances.launch(repository, repository.getSelectedInstance(), LauncherHelper::setKeep);
                    break;
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    public static boolean isStopped() {
        return decorator == null;
    }

    /// Releases controller-owned pages, window state, and JavaFX helper resources.
    public static void shutdown() {
        onApplicationStop();
        rootPage = null;
        gameInstancePage = null;
        gameListPage = null;
        downloadPage = null;
        accountListPage = null;
        settingsPage = null;
        terracottaPage = null;
        decorator = null;

        FXUtils.shutdown();
    }
}
