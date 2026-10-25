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
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Schedulers;
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
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
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

/// Provides shared launcher pages, main-window lifecycle operations, and dialogs.
@NotNullByDefault
public final class Controllers {
    /// The shown-tip key for the minimum supported Java version.
    public static final String JAVA_VERSION_TIP = "javaVersion";
    /// The shown-tip key for the disabled-JIT warning.
    public static final String JAVA_INTERPRETED_MODE_TIP = "javaInterpretedMode";
    /// The shown-tip key for the software-rendering warning.
    public static final String SOFTWARE_RENDERING = "softwareRendering";
    /// The shown-tip key storing the year of the April Fools prompt.
    public static final String APRIL_FOOLS = "aprilFools";

    /// The cached game-instance page, absent before creation and after shutdown.
    private static @Nullable GameInstancePage gameInstancePage;
    /// The lazy game-list page holder, cleared during shutdown.
    private static @Nullable Lazy<GameListPage> gameListPage = new Lazy<>(GameListPage::new);
    /// The lazy root-page holder, cleared during shutdown.
    private static @Nullable Lazy<RootPage> rootPage = new Lazy<>(RootPage::new);
    /// The coordinator for the main window's scene graph and navigation stack.
    private static @Nullable Decorator decorator;

    /// The cached download page, absent before creation and after shutdown.
    private static @Nullable DownloadPage downloadPage;
    /// The lazy account page holder with shared account bindings, cleared during shutdown.
    private static @Nullable Lazy<AccountListPage> accountListPage = new Lazy<>(() -> {
        AccountListPage accountListPage = new AccountListPage();
        accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        accountListPage.accountsProperty().bindContent(Accounts.getAccounts());
        accountListPage.authServersProperty().bindContentBidirectional(getAuthlibInjectorServers());
        return accountListPage;
    });
    /// The cached settings page, absent before creation and after shutdown.
    private static @Nullable LauncherSettingsPage settingsPage;
    /// The lazy Terracotta page holder, cleared during shutdown.
    private static @Nullable Lazy<TerracottaPage> terracottaPage = new Lazy<>(TerracottaPage::new);

    /// Prevents instantiation of this utility class.
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

    /// The shared launcher title, populated during UI initialization.
    private static @Nullable StringProperty launcherTitle;

    /// Returns the shared launcher title, creating it and scheduling integrity verification if needed.
    public static ReadOnlyStringProperty launcherTitleProperty() {
        if (launcherTitle == null) {
            launcherTitle = new SimpleStringProperty(Controllers.class, "launcherTitle", Metadata.OFFICIAL_BUILD
                    ? i18n("launcher.title", Metadata.VERSION)
                    : i18n("launcher.title.unofficial", Metadata.VERSION));
            if (Metadata.OFFICIAL_BUILD) {
                @Nullable Boolean selfVerified = IntegrityChecker.getSelfVerified();
                if (selfVerified != null) {
                    if (!selfVerified) {
                        launcherTitle.set(i18n("launcher.title.modified", Metadata.VERSION));
                    }
                } else {
                    Schedulers.io().execute(() -> {
                        if (!IntegrityChecker.isSelfVerified()) {
                            FXUtils.runInFX(() -> {
                                //noinspection DataFlowIssue
                                launcherTitle.set(i18n("launcher.title.modified", Metadata.VERSION));
                            });
                        }
                    });
                }
            }
        }

        return launcherTitle;
    }

    /// Returns the stage currently attached to the main-window decorator.
    ///
    /// @return the current stage, or `null` before initialization or after detachment
    public static @Nullable Stage getStage() {
        @Nullable Decorator currentDecorator = decorator;
        return currentDecorator == null ? null : currentDecorator.getStage();
    }

    /// Creates the cached page and attaches it for preparation during a normal window layout pulse.
    @FXThread
    public static void prepareGameInstancePage() {
        if (gameInstancePage == null) {
            gameInstancePage = getDecorator().preparePage(new GameInstancePage());
        }
    }

    /// Returns the cached game-instance page, creating it if necessary.
    @FXThread
    public static GameInstancePage getGameInstancePage() {
        if (gameInstancePage == null) {
            gameInstancePage = new GameInstancePage();
        }
        return gameInstancePage;
    }

    /// Returns the lazily created game-list page. Must be called before shutdown.
    @FXThread
    public static GameListPage getGameListPage() {
        return gameListPage.get();
    }

    /// Returns the lazily created root page. Must be called before shutdown.
    @FXThread
    public static RootPage getRootPage() {
        return rootPage.get();
    }

    /// Creates the cached page and attaches it for preparation during a normal window layout pulse.
    @FXThread
    public static void prepareSettingsPage() {
        if (settingsPage == null) {
            settingsPage = getDecorator().preparePage(new LauncherSettingsPage());
        }
    }

    /// Returns the cached settings page, creating it if necessary.
    @FXThread
    public static LauncherSettingsPage getSettingsPage() {
        if (settingsPage == null) {
            settingsPage = new LauncherSettingsPage();
        }
        return settingsPage;
    }

    /// Returns the lazily created account page. Must be called before shutdown.
    @FXThread
    public static AccountListPage getAccountListPage() {
        return accountListPage.get();
    }

    /// Creates the cached page and attaches it for preparation during a normal window layout pulse.
    @FXThread
    public static void prepareDownloadPage() {
        if (downloadPage == null) {
            downloadPage = getDecorator().preparePage(new DownloadPage());
        }
    }

    /// Returns the cached download page, creating it if necessary.
    @FXThread
    public static DownloadPage getDownloadPage() {
        if (downloadPage == null) {
            downloadPage = new DownloadPage();
        }
        return downloadPage;
    }

    /// Returns the lazily created Terracotta page. Must be called before shutdown.
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

    /// Configures the main stage's application identity, shutdown action, and taskbar relaunch information.
    ///
    /// @param stage the unshown main stage
    private static void configureMainStage(Stage stage) {
        stage.setOnCloseRequest(event -> Launcher.stopApplication());
        FXUtils.setIcon(stage);
        stage.titleProperty().bind(launcherTitleProperty());
        WindowsNativeUtils.installWindowsAppUserModelRelaunchProperties(stage);
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

        configureMainStage(stage);

        decorator = new Decorator(getRootPage(), Controllers::configureMainStage);
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
            @Nullable Number shownTipVersion = null;
            try {
                shownTipVersion = (Number) state().getShownTips().get(JAVA_VERSION_TIP);
            } catch (ClassCastException e) {
                LOG.warning("Invalid type for shown tips key: " + JAVA_VERSION_TIP, e);
            }
            if (shownTipVersion == null || shownTipVersion.intValue() < Metadata.MINIMUM_SUPPORTED_JAVA_VERSION) {
                MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("fatal.deprecated_java_version"), null, MessageType.WARNING);
                @Nullable String downloadLink = Metadata.getSuggestedJavaDownloadLink();
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

            @Nullable SupportedLocale lzh = SupportedLocale.getSupportedLocales().stream()
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

        tryInstallBundledModpack(GameDirectoryManager.getSelectedRepository());
    }

    /// Offers automatic install when a package exists under `.hmcl/modpack/`.
    ///
    /// Called from [Controllers#initialize] after the UI is ready. Install does not wait for repository
    /// refresh: instance paths come from the selected repository layout. The package file itself is the
    /// install signal; it is deleted after a successful install so later startups do not re-prompt.
    private static void tryInstallBundledModpack(HMCLGameRepository repository) {
        @Nullable Path modpackFile = Metadata.findBundledModpackFile();
        if (modpackFile == null) {
            return;
        }

        LOG.info("Found bundled modpack at " + modpackFile + "; starting automatic install");

        Controllers.taskDialog(
                Task.composeAsync(Schedulers.io(), () -> {
                            Modpack modpack = ModpackHelper.readModpackManifest(modpackFile);
                            return ModpackHelper.getInstallTask(
                                    repository, modpackFile, new GameInstanceID(modpack.getName()), modpack, null);
                        })
                        .whenComplete(Schedulers.javafx(), (ignored, exception) -> {
                            if (exception != null) {
                                LOG.warning("Failed to install bundled modpack", exception);
                                return;
                            }
                            try {
                                Files.deleteIfExists(modpackFile);
                            } catch (IOException e) {
                                LOG.warning("Failed to delete bundled modpack: " + modpackFile, e);
                            }
                        }), i18n("modpack.installing"), TaskCancellationAction.NO_CANCEL
        );
    }

    /// Shows the content if a main-window decorator is available; otherwise does nothing.
    public static void dialog(Region content) {
        if (decorator != null)
            decorator.showDialog(content);
    }

    /// Shows an informational message with the default title. See [#dialog(Region)].
    public static void dialog(String text) {
        dialog(text, null);
    }

    /// Shows a message with an optional title. A null title uses the message type's default title.
    /// Callbacks, when supplied, may be null. See [#dialog(Region)].
    public static void dialog(String text, @Nullable String title) {
        dialog(text, title, MessageType.INFO);
    }

    /// Shows a message with an optional title. A null title uses the message type's default title.
    /// Callbacks, when supplied, may be null. See [#dialog(Region)].
    public static void dialog(String text, @Nullable String title, MessageType type) {
        dialog(text, title, type, null);
    }

    /// Shows a message with an optional title. A null title uses the message type's default title.
    /// Callbacks, when supplied, may be null. See [#dialog(Region)].
    public static void dialog(String text, @Nullable String title, MessageType type, @Nullable Runnable ok) {
        dialog(new MessageDialogPane.Builder(text, title, type).ok(ok).build());
    }

    /// Shows a confirmation message with optional yes and no actions.
    /// A null title uses the message type's default title. See [#dialog(Region)].
    public static void confirm(String text, @Nullable String title, @Nullable Runnable yes, @Nullable Runnable no) {
        confirm(text, title, MessageType.QUESTION, yes, no);
    }

    /// Shows a confirmation message with optional yes and no actions.
    /// A null title uses the message type's default title. See [#dialog(Region)].
    public static void confirm(String text, @Nullable String title, MessageType type, @Nullable Runnable yes, @Nullable Runnable no) {
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

    /// Shows a confirmation message with the supplied action button and a cancel button.
    /// A null title uses the message type's default title; the cancel callback may be null. See [#dialog(Region)].
    public static void confirmAction(String text, @Nullable String title, MessageType type, ButtonBase actionButton) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, null).build());
    }

    /// Shows a confirmation message with the supplied action button and a cancel button.
    /// A null title uses the message type's default title; the cancel callback may be null. See [#dialog(Region)].
    public static void confirmAction(String text, @Nullable String title, MessageType type, ButtonBase actionButton, @Nullable Runnable cancel) {
        dialog(new MessageDialogPane.Builder(text, title, type).actionOrCancel(actionButton, cancel).build());
    }

    /// Shows a confirmation dialog whose OK button is disabled for the specified number of seconds.
    /// A null title uses the message type's default title; callbacks may be null. See [#dialog(Region)].
    ///
    /// @throws IllegalArgumentException if the number of seconds is not positive
    public static void confirmWithCountdown(String text, @Nullable String title, int seconds, MessageType messageType,
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

    /// Queues the content for display if a main-window decorator is available; otherwise does nothing.
    public static void dialogLater(Region content) {
        if (decorator != null)
            decorator.showDialogLater(content);
    }

    /// Shows a text prompt and returns its future, completed when the result callback resolves.
    /// Closing the prompt without resolving leaves the future incomplete. The title may be null.
    /// The initial value and validator array, when supplied, may be null. See [#dialog(Region)].
    public static CompletableFuture<String> prompt(@Nullable String title, FutureCallback<String> onResult) {
        return prompt(title, onResult, "");
    }

    /// Shows a text prompt and returns its future, completed when the result callback resolves.
    /// Closing the prompt without resolving leaves the future incomplete. The title may be null.
    /// The initial value and validator array, when supplied, may be null. See [#dialog(Region)].
    public static CompletableFuture<String> prompt(@Nullable String title, FutureCallback<String> onResult, @Nullable String initialValue, ValidatorBase @Nullable ... validators) {
        InputDialogPane pane = new InputDialogPane(title, initialValue, onResult, validators);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    /// Shows the supplied questions and returns the dialog's completion future. See [#dialog(Region)].
    public static CompletableFuture<List<PromptDialogPane.Builder.Question<?>>> prompt(PromptDialogPane.Builder builder) {
        PromptDialogPane pane = new PromptDialogPane(builder);
        dialog(pane);
        return pane.getCompletableFuture();
    }

    /// Creates and shows a dialog for the executor without starting it.
    /// The title may be null. See [#dialog(Region)].
    public static TaskExecutorDialogPane taskDialog(TaskExecutor executor, @Nullable String title, TaskCancellationAction onCancel) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(onCancel);
        pane.setTitle(title);
        pane.setExecutor(executor);
        dialog(pane);
        return pane;
    }

    /// Creates and shows a dialog for the task, then starts its executor.
    /// The title may be null. See [#dialog(Region)].
    public static TaskExecutorDialogPane taskDialog(Task<?> task, @Nullable String title, TaskCancellationAction onCancel) {
        TaskExecutor executor = task.executor();
        TaskExecutorDialogPane pane = taskDialog(executor, title, onCancel);
        executor.start();
        return pane;
    }

    /// Navigates to the node using the standard navigation animation. Requires an initialized decorator.
    public static void navigate(Node node) {
        decorator.navigate(node, ContainerAnimations.NAVIGATION, Motion.SHORT4, Motion.EASE);
    }

    /// Navigates to the node using the forward animation. Requires an initialized decorator.
    public static void navigateForward(Node node) {
        decorator.navigate(node, ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    /// Shows a toast in the main window. Requires an initialized decorator.
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

    /// Dispatches supported hmcl links or opens other links externally; ignores unknown internal links.
    public static void onHyperlinkAction(String href) {
        if (href.startsWith("hmcl://")) {
            switch (href) {
                case "hmcl://settings/feedback":
                    Controllers.getSettingsPage().showFeedback();
                    Controllers.navigate(Controllers.getSettingsPage());
                    break;
                case "hmcl://game/launch":
                    var repository = GameDirectoryManager.getSelectedRepository();
                    Instances.launch(repository.getSelectedInstance(), LauncherHelper::setKeep);
                    break;
            }
        } else {
            FXUtils.openLink(href);
        }
    }

    /// Offers to open the URI externally or copy it to the clipboard; does nothing for null.
    public static void openUriOrCopy(@Nullable URI url) {
        if (url == null) return;
        openUriOrCopy(url.toString());
    }

    /// Offers to open the URI externally or copy it to the clipboard; does nothing for null.
    public static void openUriOrCopy(@Nullable String url) {
        if (url == null) return;
        var dialog = new MessageDialogPane.Builder(
                i18n("web.open_in_browser", url),
                i18n("message.confirm"),
                MessageDialogPane.MessageType.QUESTION
        )
                .addAction(i18n("button.copy"), () -> FXUtils.copyText(url))
                .yesOrNo(() -> FXUtils.openLink(url), null)
                .build();
        dialog(dialog);
    }

    /// Returns whether no main-window decorator is retained, including before initialization and after shutdown.
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

    /// Whether heap trimming is enabled; disabled by an HMCL_AUTO_TRIM_HEAP value equal to false, ignoring case.
    public static final boolean AUTO_TRIM_HEAP = !"false".equalsIgnoreCase(System.getenv("HMCL_AUTO_TRIM_HEAP"));

    /// Requests garbage collection if [#AUTO_TRIM_HEAP] is enabled.
    public static void trimHeap() {
        if (AUTO_TRIM_HEAP) {
            System.gc();
        }
    }
}
