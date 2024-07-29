package net.burningtnt.hmat.solver;

import org.jackhuang.hmcl.task.Task;

public interface Solver {
    int BTN_NEXT = 0;

    /**
     * Executed in FXThread.
     */
    void configure(SolverConfigurator configurator);

    /**
     * Executed in FXThread.
     *
     * @param selectionID BTN_NEXT if user click 'Next'. Others if user click selection buttons.
     */
    void callbackSelection(SolverConfigurator configurator, int selectionID);

    static Solver ofTask(Task<?> task) {
        return new Solver() {
            @Override
            public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                configurator.transferTo(null);
            }

            @Override
            public void configure(SolverConfigurator configurator) {
                configurator.bindTask(task);
            }
        };
    }
}
