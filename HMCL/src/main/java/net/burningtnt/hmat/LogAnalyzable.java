package net.burningtnt.hmat;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.util.List;

public final class LogAnalyzable {
    private final Version version;
    private final LibraryAnalyzer analyzer;
    private final HMCLGameRepository repository;

    private final ManagedProcess managedProcess;
    private final ProcessListener.ExitType exitType;
    private final LaunchOptions launchOptions;

    private final List<String> logs;

    public LogAnalyzable(Version version, LibraryAnalyzer analyzer, HMCLGameRepository repository, ManagedProcess managedProcess, ProcessListener.ExitType exitType, LaunchOptions launchOptions, List<String> logs) {
        this.version = version;
        this.analyzer = analyzer;
        this.repository = repository;
        this.managedProcess = managedProcess;
        this.exitType = exitType;
        this.launchOptions = launchOptions;
        this.logs = logs;
    }

    public Version getVersion() {
        return version;
    }

    public LibraryAnalyzer getAnalyzer() {
        return analyzer;
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public ManagedProcess getManagedProcess() {
        return managedProcess;
    }

    public ProcessListener.ExitType getExitType() {
        return exitType;
    }

    public LaunchOptions getLaunchOptions() {
        return launchOptions;
    }

    public List<String> getLogs() {
        return logs;
    }
}
