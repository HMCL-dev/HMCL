package net.burningtnt.hmat.game;

import net.burningtnt.hmat.AnalyzeResult;
import net.burningtnt.hmat.Analyzer;
import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import net.burningtnt.hmat.solver.SolverConfigurator;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.IOException;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VirtualMemoryAnalyzer implements Analyzer<LogAnalyzable> {
    private static final String KEY = "There is insufficient memory for the Java Runtime Environment to continue.";

    @Override
    public ControlFlow analyze(LogAnalyzable input, List<AnalyzeResult<LogAnalyzable>> results) {
        List<String> logs = input.getLogs();
        int l = logs.size();

        for (int i = Math.max(0, l - 10); i < l; i++) {
            if (logs.get(i).contains(KEY)) {
                results.add(new AnalyzeResult<>(this, AnalyzeResult.ResultID.LOG_GAME_VIRTUAL_MEMORY, new Solver() {
                    private int BTN_OPEN_SYS_DM = -1;

                    @Override
                    public void configure(SolverConfigurator configurator) {
                        configurator.setDescription(i18n("analyzer.result.log_game_virtual_memory.steps.1"));
                        configurator.setImage(FXUtils.newBuiltinImage("/assets/img/hmat/log/game/virtual_memory/step_1.png"));

                        BTN_OPEN_SYS_DM = configurator.putButton(i18n("analyzer.result.log_game_virtual_memory.button.open_sys_dm"));
                    }

                    @Override
                    public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                        if (selectionID == BTN_OPEN_SYS_DM) {
                            try {
                                Runtime.getRuntime().exec(new String[]{
                                        "rundll32.exe",
                                        "shell32.dll,Control_RunDLL",
                                        "sysdm.cpl"
                                });
                            } catch (IOException e) {
                                Logger.LOG.warning("Cannot open sysdm.", e);
                            }
                        } else if (selectionID == BTN_NEXT) {
                            configurator.transferTo(new Solver() {
                                @Override
                                public void configure(SolverConfigurator configurator) {
                                    configurator.setDescription(i18n("analyzer.result.log_game_virtual_memory.steps.2"));
                                    configurator.setImage(FXUtils.newBuiltinImage("/assets/img/hmat/log/game/virtual_memory/step_2.png"));
                                }

                                @Override
                                public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                                    if (selectionID == BTN_NEXT) {
                                        configurator.transferTo(new Solver() {
                                            private int BTN_REBOOT_COMPUTER = -1;

                                            @Override
                                            public void configure(SolverConfigurator configurator) {
                                                configurator.setDescription(i18n("analyzer.result.log_game_virtual_memory.steps.3"));
                                                configurator.setImage(FXUtils.newBuiltinImage("/assets/img/hmat/log/game/virtual_memory/step_3.png"));

                                                BTN_REBOOT_COMPUTER = configurator.putButton(i18n("analyzer.result.log_game_virtual_memory.button.reboot_computer"));
                                            }

                                            @Override
                                            public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                                                if (selectionID == BTN_NEXT || selectionID == BTN_REBOOT_COMPUTER) {
                                                    Launcher.rebootComputer();
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                }));
                return ControlFlow.BREAK_OTHER;
            }
        }

        return ControlFlow.CONTINUE;
    }
}
