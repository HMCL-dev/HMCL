package net.burningtnt.hmat.solver;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.task.Task;

public interface SolverConfigurator {
    void setImage(Image image);

    void setDescription(String description);

    int putButton(String text);

    /**
     * Make this step automatically resolved.
     */
    void bindTask(Task<?> task);

    /**
     * Transfer to another Solver.
     * @param solver Another solver. null if no further solver is provided.
     */
    void transferTo(Solver solver);
}
