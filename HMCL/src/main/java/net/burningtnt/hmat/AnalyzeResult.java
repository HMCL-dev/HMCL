package net.burningtnt.hmat;

import net.burningtnt.hmat.solver.Solver;

public final class AnalyzeResult<T> {
    private final Analyzer<T> analyzer;

    private final ResultID resultID;

    private final Solver Solver;

    public AnalyzeResult(Analyzer<T> analyzer, ResultID resultID, Solver solver) {
        this.analyzer = analyzer;
        this.resultID = resultID;
        Solver = solver;
    }

    public Analyzer<T> getAnalyzer() {
        return analyzer;
    }

    public ResultID getResultID() {
        return resultID;
    }

    public Solver getSolver() {
        return Solver;
    }

    public enum ResultID {
        LOG_GAME_CODE_PAGE,
        LOG_GAME_VIRTUAL_MEMORY,
        LOG_GAME_JRE_32BIT,
        LOG_GAME_JRE_INVALID,
        LOG_GAME_JRE_VERSION,
        LOG_GAME_MOD_REQUIREMENT,
        LOG_GAME_BAD_MOD
    }
}
