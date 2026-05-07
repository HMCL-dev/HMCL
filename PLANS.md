# Complete GameSetting Migration Plan

## Summary

Migrate game settings from the old `VersionSetting` model to the new `GameSetting` model. Global settings will become a list of `GameSetting.Global` entries stored in `Config`; each global setting has a `UUID` and editable `name`. Instance settings will be stored in `versions/<id>/hmcl-game-setting.cfg`. The old `hmclversion.cfg` files and old `Profile.global` data must remain untouched. Launching, exporting, installing, and the settings UI should all read effective `GameSetting` values.

## Key Changes

- Extend the data model:
  - Add `UUID id` and `String name` to `GameSetting.Global`.
  - Add `ObservableList<GameSetting.Global>` and `UUID defaultGameSetting` to `Config`.
  - Add a migration-only `legacyGameSettingParent` field to `Profile` to record the global setting UUID converted from that profile's old global setting.
  - Resolve `GameSetting.Instance.parent` as follows: explicit instance parent first, then `Config.defaultGameSetting`; during migration only, an unsaved converted old instance may use `Profile.legacyGameSettingParent`.
- Implement compatibility migration:
  - If the new global settings list is absent, convert each old `Profile.global` into one `GameSetting.Global`, name it from the profile display name, and write the UUID into `legacyGameSettingParent`.
  - Load instance settings from `hmcl-game-setting.cfg` first. If missing, convert old `hmclversion.cfg` into a transient `GameSetting.Instance`.
  - Do not rewrite old config files. Create `hmcl-game-setting.cfg` only after the user changes instance settings or the repository explicitly saves the new instance setting.
  - If multiple old profiles share the same physical instance with different legacy parents, the first saved new instance setting wins; later conflicts should only be logged.
- Define effective setting resolution:
  - Memory settings are overridden as a group.
  - `jvmOptions`, `gameArgs`, and `environmentVariables` merge when inherited, with global values first and instance values second.
  - `commandWrapper`, `preLaunchCommand`, and `postExitCommand` inherit per field and do not merge.
  - Java, window, Quick Play, logging, renderer, native library, and check-related options inherit per field.
  - `GameWindowType.MAXIMIZED` is saved and displayed only; launching should currently treat it like a normal windowed launch.
- Finish UI and launch integration:
  - Make `GameSettingPage` load and save real settings instead of creating temporary test objects.
  - Add global setting management UI for selecting, creating, renaming, copying, deleting global settings, and setting `Config.defaultGameSetting`.
  - Add instance UI for selecting or clearing parent UUID, showing inherited source, and binding all existing controls to `GameSetting`.
  - Update `HMCLGameRepository`, `LauncherHelper`, export, and install flows to use effective `GameSetting`.
  - Support Quick Play values for none, multiplayer, singleplayer, and realms.
  - Apply `Config.defaultGameSetting.defaultIsolationType` when deciding the default isolation strategy for newly installed instances.

## Test Plan

- Run IDEA build and `./gradlew -g .gradle-user-home compileJava test`.
- Add unit tests for old `VersionSetting` to `GameSetting` conversion, parent fallback, merged field order, and old-file preservation.
- Verify these integration scenarios:
  - A launcher with only old config starts, opens settings, and creates the global setting list plus migration UUIDs.
  - Editing an instance creates only `hmcl-game-setting.cfg` and does not modify `hmclversion.cfg`.
  - Different instance parent UUIDs produce different effective launch options.
  - JVM options, game arguments, and environment variables merge in the intended order.
  - Quick Play creates the correct `QuickPlayOption` for multiplayer, singleplayer, and realms.

## Assumptions

- The new instance setting file name is `hmcl-game-setting.cfg`.
- `VersionSetting` can remain temporarily as a migration reader and compatibility conversion source, but new runtime paths should no longer depend on it.
- Deleting a `GameSetting.Global` that is used as the default or referenced by an instance should be blocked, or the user must switch references first.
