# Hello Minecraft! Launcher [![Build Status](https://travis-ci.org/huanghongxun/HMCL.svg?branch=master)](https://travis-ci.org/huanghongxun/HMCL)
GPL v3, see http://www.gnu.org/licenses/gpl.html

## Introduction

HMCL is a Minecraft launcher which supports Mod management, game customizing, auto installing(Forge, LiteLoader and OptiFine), modpack creating, UI customizing and so on.

## Contribution
If you want to submit a pull request, there're some requirements:
* IDE: Intellij IDEA.
* Compiler: Java 1.8.
* Do NOT modify `gradle` files.

## HMCLCore
Now HMCLCore is independent and you can use HMCLCore as a library to launch your game.

### GameRepository
Create a game repository `repository` to manage a minecraft installation. Like this.
```java
DefaultGameRepository repository = new DefaultGameRepository(new File(".minecraft").getAbsoluteFile());
```

You should put where your minecraft installation is to the only argument of the constructor of `DefaultGameRepository`.

### Launching
Now you can launch game by constructing a `DefaultLauncher`.
```java
DefaultLauncher launcher = new DefaultLauncher(
        repository, // GameRepository
        "test", // Your minecraft version name
        OfflineAccountFactory.INSTANCE.fromUsername("player007").logIn(MultiCharacterSelector.DEFAULT), // account
        // or YggdrasilAccountFactory.INSTANCE.fromUsername(username, password).logIn
        new LaunchOptions.Builder()
        		.setGameDir(repository.getBaseDirectory())
        		.setMaxMemory(...)
        		.setJava(...)
        		.setJavaArgs(...)
        		.setMinecraftArgs(...)
        		.setHeight(...)
        		.setWidth(...)
        		...
        		.create(), 
        new ProcessListener() { // listening the process state.
            @Override
            public void onLog(String log, Log4jLevel level) { // new console log
                System.out.println(log);
            }
            
            @Override
            public void onExit(int exitCode, ExitType exitType) { // process exited
                System.out.println("Process exited then exit code " + exitCode);
            }
        },
        false // true if launcher process exits, listening thread exit too.
);
```
Now you can simply call `launcher.launch()` to launch the game.
If you want the command line, just call `launcher.getRawCommandLine`. Also, `StringUtils.makeCommand` might be useful.

### Downloading
HMCLCore just owns a simple way to download a new game.
```java
DefaultDependencyManager dependency = new DefaultDependencyManager(repository, MojangDownloadProvider.INSTANCE, proxy);
```
`repository` is your `GameRepository`. `MojangDownloadProvider.INSTANCE` means that we download files from mojang servers. If you want BMCLAPI, `BMCLAPIDownloadProvider.INSTANCE` is just for you. `proxy` is `java.net.Proxy`, if you have a proxy, put it here, or `Proxy.NO_PROXY`.

Now `GameBuilder` can build a game.
```
Task gameBuildingTask = dependency.gameBuilder()
                .name("test")
                .gameVersion("1.12") // Minecraft version
                .version("forge", "14.21.1.2426") // Forge version
                .version("liteloader", "1.12-SNAPSHOT-4") // LiteLoader version
                .version("optifine", "HD_U_C4") // OptiFine version
                .buildAsync()
```

Nowadays HMCLCore only supports Forge, LiteLoader and OptiFine auto-installing.
`buildAsync` will return a `Task`, you can call `Task.executor()::start` or simply `Task::start` to start this task. If you want to monitor the execution of tasks, you should see `TaskExecutor` and `Task::executor`.

## HMCL
JavaFX version of HMCL does not support old APIs.