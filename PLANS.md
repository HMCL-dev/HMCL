# Complete GameSettings Migration Plan

## Summary

Migrate game settings from the old JSON format to the new `GameSettings` model. Global game setting presets will become a list of `GameSettings.Preset` entries stored in `game-setting-presets.json`; each preset has a `UUID` and editable `name`. Instance settings will be stored in `versions/<id>/hmcl-game-setting.cfg`. The old `hmclversion.cfg` files and old `Profile.global` data must remain untouched. Launching, exporting, installing, and the settings UI should all read effective `GameSettings` values.

## Key Changes

- Extend the data model:
  - Add `UUID id` and `String name` to `GameSettings.Preset`.
  - Add `GameSettingsPresets` for `ObservableList<GameSettings.Preset>` and `UUID defaultGameSetting`.
  - Add a migration-only `legacyGameSettingParent` field to `Profile` to record the global setting UUID converted from that profile's old global setting.
  - Resolve `GameSettings.Instance.parent` as follows: explicit instance parent first, then `defaultGameSetting`; during migration only, an unsaved converted old instance may use `Profile.legacyGameSettingParent`.
- Implement compatibility migration:
  - If the new preset list is absent, convert each old `Profile.global` into one `GameSettings.Preset`, name it from the profile display name, generate a deterministic UUID from the legacy profile key, and write that UUID into `legacyGameSettingParent`.
  - If a deterministic migration UUID is already occupied by another global setting, fall back to a newly generated unique random UUID for that migrated setting.
  - Load instance settings from `hmcl-game-setting.cfg` first. If missing, convert old `hmclversion.cfg` into a transient `GameSettings.Instance`.
  - Do not rewrite old config files. Create `hmcl-game-setting.cfg` only after the user changes instance settings or the repository explicitly saves the new instance setting.
  - If multiple old profiles share the same physical instance with different legacy parents, the first saved new instance setting wins; later conflicts should only be logged.
- Define effective setting resolution:
  - Memory settings are inherited or overridden as a group.
  - `jvmOptions`, `gameArgs`, and `environmentVariables` are inherited or overridden as complete values. They do not merge global and instance text.
  - `commandWrapper`, `preLaunchCommand`, and `postExitCommand` inherit per field and do not merge.
  - Java, window, Quick Play, logging, renderer, native library, and check-related options inherit per field.
  - `GameWindowType.MAXIMIZED` is saved and displayed only; launching should currently treat it like a normal windowed launch.
- Finish UI and launch integration:
  - Make `GameSettingsPage` load and save real settings instead of creating temporary test objects.
  - Add global setting management UI for selecting, creating, renaming, copying, deleting global settings, and setting `Config.defaultGameSetting`.
  - Add instance UI for selecting or clearing parent UUID, showing inherited source, and binding all existing controls to `GameSettings`.
  - Update `HMCLGameRepository`, `LauncherHelper`, export, and install flows to use effective `GameSettings`.
  - Support Quick Play values for none, multiplayer, singleplayer, and realms.
  - Apply `Config.defaultGameSetting.defaultIsolationType` when deciding the default isolation strategy for newly installed instances.

## Test Plan

- Run IDEA build and `./gradlew -g .gradle-user-home compileJava test`.
- Add unit tests for old JSON to `GameSettings` conversion, deterministic migration UUIDs, parent fallback, override behavior, and old-file preservation.
- Verify these integration scenarios:
  - A launcher with only old config starts, opens settings, and creates the global setting list plus migration UUIDs.
  - Editing an instance creates only `hmcl-game-setting.cfg` and does not modify `hmclversion.cfg`.
  - Different instance parent UUIDs produce different effective launch options.
  - JVM options, game arguments, and environment variables inherit from the selected global setting unless the instance explicitly overrides them.
  - Quick Play creates the correct `QuickPlayOption` for multiplayer, singleplayer, and realms.

## Assumptions

- The new instance setting file name is `hmcl-game-setting.cfg`.
- Old local settings are read as raw JSON and immediately converted into `GameSettings.Instance`; runtime paths should never keep old setting objects.
- Deleting a `GameSettings.Preset` that is used as the default or referenced by an instance should be blocked, or the user must switch references first.
