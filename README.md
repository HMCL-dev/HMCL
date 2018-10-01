# Hello Minecraft! Launcher [![Build Status](https://ci.huangyuhui.net/job/HMCL/badge/icon?.svg)](https://ci.huangyuhui.net/job/HMCL)
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
        new AccountBuilder.Builder()
                .setUsername("playerId")
                .setProxy(Proxy.NO_PROXY) // Optional
                .create(OfflineAccountFactory.INSTANCE)
                .logIn(), // account
        // or new AccountBuilder.Builder()
        //            .setUsername("someone@xxx.com")
        //            .setPassword("someone's password")
        //            // for Mojang account
        //            .create(new YggdrasilAccountFactory(MojangYggdrasilProvider.INSTANCE))
        //            // for Authlib Injector account
        //            .create(new AuthlibInjectorAccountFactory(
        //                new AuthlibInjectorDownloader(new File("path to save executables of authlib injector"),
        //                        () -> MojangYggdrasilProvider.INSTANCE)::getArtifactInfo,
        //                () -> AuthlibInjectorServer.fetchServerInfo("Your authlib injector auth server")))
        //            .logIn()
        new LaunchOptions.Builder()
        		.setGameDir(repository.getBaseDirectory()) // directory that the game saves settings to
        		.setMaxMemory(...)
        		.setJava(...) // executable of JVM
        		.setJavaArgs(...) // additional Java VM arguments
        		.setMinecraftArgs(...) // additional Minecraft arguments
        		.setHeight(...) // height of game window
        		.setWidth(...)  // width of game window
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
        false // true if launcher process exits, listening threads exit too.
);
```
Now you can simply call `launcher.launch()` to launch the game.
If you want the command line, just call `launcher.getRawCommandLine`. Also, `StringUtils.makeCommand` might be useful.

### Downloading
HMCLCore just owns a simple way to download a new game.
```java
DefaultDependencyManager dependency = new DefaultDependencyManager(repository, new MojangDownloadProvider(), proxy);
```
`repository` is your `GameRepository`. `MojangDownloadProvider.INSTANCE` means that we download files from mojang servers. If you want BMCLAPI, `BMCLAPIDownloadProvider.INSTANCE` is just for you. `proxy` is `java.net.Proxy`, if you have a proxy, put it here, or `Proxy.NO_PROXY`.

Now `GameBuilder` can build a game.
```java
Task gameBuildingTask = dependency.gameBuilder()
                .name("test")
                .gameVersion("1.12") // Minecraft version
                .version("forge", "14.21.1.2426") // Forge version
                .version("liteloader", "1.12-SNAPSHOT-4") // LiteLoader version
                .version("optifine", "HD_U_C4") // OptiFine version
                .buildAsync();
```

Nowadays HMCLCore only supports Forge, LiteLoader and OptiFine auto-installing.
`buildAsync` will return a `Task`, you can call `Task.executor()::start` or simply `Task::start` to start this task. If you want to monitor the execution of tasks, you should see `TaskExecutor` and `Task::executor`.

### Modpack installing

HMCLCore supports Curse, MultiMC modpack.

```java
// Installing curse modpack
new CurseInstallTask(dependency, modpackZipFile, CurseManifest.readCurseForgeModpackManifest(modpackZipFile), "name of the new game");

// Installing MultiMC modpack
new MultiMCModpackInstallTask(dependency, modpackZipFile, MultiMCInstanceConfiguration.readMultiMCModpackManifest(modpackZipFile), "name of the new game");
// ** IMPORTANT **: You should read game settings from MultiMCInstanceConfiguration
```

## HMCL

No plugin API.

## JVM Options (for debugging)
|Parameter|Description|
|---------|-----------|
|`-Dhmcl.self_integrity_check.disable=true`|Bypass the self integrity check when checking for update.|
|`-Dhmcl.version.override=<version>`|Override the version number.|
|`-Dhmcl.update_source.override=<url>`|Override the update source.|
