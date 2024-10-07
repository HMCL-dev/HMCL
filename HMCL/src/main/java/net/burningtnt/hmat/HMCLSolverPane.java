package net.burningtnt.hmat;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import net.burningtnt.hmat.solver.Solver;
import net.burningtnt.hmat.solver.SolverConfigurator;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ImageViewStage;
import org.jackhuang.hmcl.util.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class HMCLSolverPane<T> extends StackPane {
    public static final int STATE_ANALYZING = 0;

    public static final int STATE_FINISHED = 1;

    public static final int STATE_REQUEST_REBOOT_GAME = 2;

    private final Iterator<AnalyzeResult<T>> results;

    private final VBox solverContainer = new VBox(8);

    private final JFXButton next = FXUtils.newRaisedButton(i18n("wizard.next"));

    private final Label info = new Label();

    private final HMCLSolverController controller = new HMCLSolverController();

    private final IntegerProperty state = new SimpleIntegerProperty(STATE_ANALYZING);

    private AnalyzeResult<T> currentResult;

    private Solver currentSolver;

    public IntegerProperty stateProperty() {
        return state;
    }

    public HMCLSolverPane(Iterator<AnalyzeResult<T>> results) {
        this.results = results;

        VBox.setVgrow(solverContainer, Priority.ALWAYS);
        solverContainer.setPadding(new Insets(0, 0, 0, 8));
        if (!results.hasNext()) {
            throw new IllegalStateException("No AnalyzeResult.");
        } else {
            controller.transferTo(null);
        }

        VBox container = new VBox(8);

        HBox titleBar = new HBox(8);
        titleBar.getStyleClass().addAll("jfx-tool-bar-second", "depth-1", "padding-8");
        titleBar.setAlignment(Pos.BASELINE_LEFT);
        HBox spacing = new HBox();
        HBox.setHgrow(spacing, Priority.ALWAYS);
        titleBar.getChildren().setAll(info, spacing, next);

        container.getChildren().setAll(titleBar, solverContainer);

        StackPane.setAlignment(container, Pos.BOTTOM_LEFT);
        getChildren().setAll(container);
    }

    private void update() {
        if (controller.state == null) {
            solverContainer.setAlignment(Pos.CENTER);
            JFXProgressBar progressBar = new JFXProgressBar();
            progressBar.setProgress(1D);
            solverContainer.getChildren().setAll(progressBar, new Label(i18n("analyzer.solved")));

            next.setText(i18n("analyzer.launch_again"));
            next.setDisable(false);
            next.setOnAction(e -> state.set(STATE_REQUEST_REBOOT_GAME));
            return;
        }

        info.setText(i18n("analyzer.progress", i18n("analyzer.result." + currentResult.getResultID().name().toLowerCase(Locale.ROOT) + ".title")));

        switch (controller.state) {
            case AUTO: {
                solverContainer.setAlignment(Pos.CENTER);
                JFXProgressBar progressBar = new JFXProgressBar();
                Task<?> task = controller.task;
                if (task == null) {
                    throw new IllegalStateException("Illegal state AUTO.");
                }
                progressBar.progressProperty().bind(task.progressProperty());
                next.setDisable(true);
                Label txt = new Label(i18n("analyzer.processing"));

                solverContainer.getChildren().setAll(progressBar, txt);

                task.whenComplete(Schedulers.javafx(), exception -> {
                    if (exception == null) {
                        currentSolver.callbackSelection(controller, 0);
                    } else {
                        progressBar.progressProperty().unbind();
                        progressBar.setProgress(1);
                        TextFlow flow = FXUtils.segmentToTextFlow(i18n("analyzer.failed"), Controllers::onHyperlinkAction);
                        flow.setTextAlignment(TextAlignment.CENTER);
                        solverContainer.getChildren().setAll(progressBar, flow);
                    }
                }).start();
                break;
            }
            case MANUAL: {
                solverContainer.setAlignment(Pos.BASELINE_LEFT);
                solverContainer.getChildren().clear();
                next.setDisable(false);
                next.setOnAction(e -> currentSolver.callbackSelection(controller, 0));
                if (controller.description != null) {
                    TextFlow flow = FXUtils.segmentToTextFlow(controller.description, Controllers::onHyperlinkAction);
                    HBox box = new HBox();
                    box.getChildren().setAll(flow);
//                    box.setStyle("-fx-border-color: red;");
                    solverContainer.getChildren().add(box);
                }
                if (!controller.buttons.isEmpty()) {
                    HBox buttons = new HBox(8);
                    for (Pair<String, Integer> btnInfo : controller.buttons) {
                        Button button = FXUtils.newBorderButton(btnInfo.getKey());
                        button.setOnAction(e -> currentSolver.callbackSelection(controller, btnInfo.getValue()));
                        buttons.getChildren().add(button);
                    }
                    solverContainer.getChildren().add(buttons);
                }
                if (solverContainer.getChildren().isEmpty()) {
                    throw new IllegalStateException("Illegal state MANUAL.");
                }
                if (controller.image != null) {
                    HBox pane = new HBox();

                    ImageView view = new ImageView(controller.image);
                    view.setPreserveRatio(true);
                    view.fitWidthProperty().bind(pane.widthProperty());
                    view.fitHeightProperty().bind(pane.heightProperty());
                    view.setOnMouseClicked(e -> new ImageViewStage(view.getImage()));
                    pane.getChildren().setAll(view);

                    pane.setAlignment(Pos.CENTER_LEFT);
                    pane.setMinWidth(0);
                    pane.prefWidthProperty().bind(solverContainer.widthProperty());
                    pane.maxWidthProperty().bind(solverContainer.widthProperty());
                    pane.setMinHeight(0);
                    pane.setPrefHeight(0);
                    VBox.setVgrow(pane, Priority.ALWAYS);

                    solverContainer.getChildren().add(pane);
                }
            }
        }
    }

    private enum State {
        AUTO, MANUAL
    }

    private final class HMCLSolverController implements SolverConfigurator {
        private State state = null;

        private String description;

        private Image image;

        private Task<?> task;

        private final List<Pair<String, Integer>> buttons = new ArrayList<>();

        @Override
        public void setImage(Image image) {
            if (state != null && state != State.MANUAL) {
                throw new IllegalStateException("State " + state + " doesn't allowed setImage.");
            }
            state = State.MANUAL;
            this.image = image;
        }

        @Override
        public void setDescription(String description) {
            if (state != null && state != State.MANUAL) {
                throw new IllegalStateException("State " + state + " doesn't allowed setImage.");
            }
            state = State.MANUAL;
            this.description = description;
        }

        @Override
        public void bindTask(Task<?> task) {
            if (state != null && state != State.AUTO) {
                throw new IllegalStateException("State " + state + " doesn't allowed setImage.");
            }
            state = State.AUTO;
            this.task = task;
        }

        @Override
        public int putButton(String text) {
            if (state != null && state != State.MANUAL) {
                throw new IllegalStateException("State " + state + " doesn't allowed setImage.");
            }
            state = State.MANUAL;
            // 0 - 255 are kept for internal use.
            int id = this.buttons.size() + 255;

            this.buttons.add(Pair.pair(text, id));
            return id;
        }

        @Override
        public void transferTo(Solver solver) {
            state = null;
            description = null;
            image = null;
            task = null;
            buttons.clear();

            if (solver != null) {
                (currentSolver = solver).configure(this);
            } else if (results.hasNext()) {
                (currentSolver = (currentResult = results.next()).getSolver()).configure(this);
            } else {
                HMCLSolverPane.this.state.set(STATE_FINISHED);
            }

            update();
        }
    }
}
