# Settings Migration Notes

## Storage Layout

- `.hmcl/config/launcher-settings.json`: workspace launcher settings.
- `.hmcl/state/launcher-state.json`: workspace UI/runtime state.
- `.hmcl/config/authlib-injector-servers.json`: workspace authlib-injector server list.
- `.hmcl/cache/authlib-injector-server-metadata.json`: workspace authlib-injector server metadata cache.
- `.hmcl/config/game-directories.json`: workspace game directory profiles in the `directories` list.
- `HMCL_USER_HOME/config/user-game-directories.json`: shared game directory profiles in the `directories` list.
- `.hmcl/config/game-settings.json`: workspace `GameSettings.Preset` entries.
- `.hmcl/config/accounts.json`: workspace portable account metadata in the `accounts` list.
- `HMCL_USER_HOME/config/user-accounts.json`: shared account metadata in the `accounts` list.
- `.hmcl/credentials/account-credentials.json`: protected workspace account token credentials.
- `HMCL_USER_HOME/credentials/user-account-credentials.json`: protected shared account token credentials.
- `versions/<id>/.hmcl/config/instance-game-settings.json`: instance-specific game settings.
- `HMCL_USER_HOME/state/*.migration-receipt.json` and `.hmcl/state/*.migration-receipt.json`: migration receipts.

## Migration Scope

- Only configuration formats used by `upstream/main` need migration support.
- File formats introduced by this branch are still unstable and do not need compatibility with earlier revisions of this branch.
- Do not add schema versions, migration paths, or compatibility shims only for intermediate file shapes produced by this branch.
- Legacy config files are read as migration inputs and must not be rewritten.
- Detached settings files should be created and saved only through the new storage model.
- Do not encode `upstream/main` wording in production code, comments, logs, schemas, or tests; keep that local baseline note in this file only.

## Migration Rules

- Legacy `hmcl.json` and `.hmcl.json` are read as workspace migration inputs.
- Legacy `accounts` fields in the workspace config are extracted into `accounts.json` and `account-credentials.json`.
- Legacy shared `accounts.json` is used only as a migration input when `user-accounts.json` does not exist.
- Legacy `authlibInjectorServers` and `addedLittleSkin` fields are extracted into `authlib-injector-servers.json`.
- Legacy profile data from `configurations` is converted into `game-directories.json`.
- Legacy profile global settings are converted into `GameSettings.Preset` entries.
- Legacy per-instance `hmclversion.cfg` files are converted into `GameSettings.Instance` data.

## Schema Policy

- Detached settings files use `$schema` with `https://schemas.glavo.site/hmcl/<id>/<version>`.
- Schema documents are stored in `docs/schemas/<id>/<version>.json` as the source files for the `/hmcl/` URL space.
- Schema versions are written as `major.minor.patch`; `major.minor` may be accepted as patch `0` when reading.
- Unsupported major versions are rejected.
- Newer minor versions may be read but must not be overwritten.
- Patch-compatible files may be saved while preserving the original schema string and unknown serialized members.

## Settings Semantics

- Instance settings resolve effective values from their selected parent preset.
- Inheritable fields preserve explicit overrides and default to parent values when not overridden.

## Verification Focus

- Loading an old config should create detached files without losing selected account, selected directory, or selected instance state.
- Editing accounts should update `accounts.json` and `account-credentials.json`, not `launcher-settings.json`.
- Existing shared `accounts.json` should migrate to `user-accounts.json` and `user-account-credentials.json`.
- Launch, export, install, and settings UI flows should read effective `GameSettings` values.
