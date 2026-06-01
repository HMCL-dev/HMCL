# Settings Migration Notes

## Storage Layout

- `settings.json`: workspace launcher settings.
- `state.json`: workspace UI/runtime state.
- `authlib-injector-servers.json`: workspace authlib-injector server list.
- `game-directories.json`: workspace game directory profiles in the `directories` list.
- `user-game-directories.json`: shared game directory profiles in the `directories` list.
- `game-settings.json`: workspace `GameSettings.Preset` entries.
- `game-accounts.json`: workspace portable account storages in the `accounts` list.
- `user-game-accounts.json`: shared account storages in the `accounts` list.
- `versions/<id>/.hmcl/instance-game-settings.json`: instance-specific game settings.

## Migration Scope

- Only configuration formats used by `upstream/main` need migration support.
- File formats introduced by this branch are still unstable and do not need compatibility with earlier revisions of this branch.
- Legacy config files are read as migration inputs and must not be rewritten.
- Detached settings files should be created and saved only through the new storage model.

## Migration Rules

- Legacy `hmcl.json` and `.hmcl.json` are read as workspace migration inputs.
- Legacy `accounts` fields in the workspace config are extracted into `game-accounts.json`.
- Legacy shared `accounts.json` is used only as a migration input when `user-game-accounts.json` does not exist.
- Legacy `authlibInjectorServers` and `addedLittleSkin` fields are extracted into `authlib-injector-servers.json`.
- Legacy profile data from `configurations` is converted into `game-directories.json`.
- Legacy profile global settings are converted into `GameSettings.Preset` entries.
- Legacy per-instance `hmclversion.cfg` files are converted into `GameSettings.Instance` data.

## Schema Policy

- Detached settings files use `$schema` with `https://schemas.glavo.site/hmcl/<id>/<version>`.
- Schema versions are written as `major.minor.patch`; `major.minor` may be accepted as patch `0` when reading.
- Unsupported major versions are rejected.
- Newer minor versions may be read but must not be overwritten.
- Patch-compatible files may be saved while preserving the original schema string and unknown serialized members.

## Settings Semantics

- Instance settings resolve effective values from their selected parent preset.
- Inheritable fields preserve explicit overrides and default to parent values when not overridden.

## Verification Focus

- Loading an old config should create detached files without losing selected account, selected directory, or selected instance state.
- Editing accounts should update `game-accounts.json`, not `settings.json`.
- Existing shared `accounts.json` should migrate to `user-game-accounts.json`.
- Launch, export, install, and settings UI flows should read effective `GameSettings` values.
