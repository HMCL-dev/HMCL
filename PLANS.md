# GameSettings Migration Notes

## Current Storage Layout

- `settings.json` stores launcher configuration that is still part of the main workspace config.
- `state.json` stores launcher UI/runtime state.
- `authlib-injector-servers.json` stores authlib-injector server entries.
- `game-directories.json` stores game directory profiles.
- `game-settings.json` stores reusable `GameSettings.Preset` entries.
- `game-accounts.json` stores account storage entries in an object with an `accounts` list.
- `versions/<id>/.hmcl/instance-game-settings.json` stores instance-specific game settings.

## Migration Rules

- Legacy `hmcl.json` and `.hmcl.json` are read as migration inputs and left untouched.
- Legacy `accounts` fields are extracted from the main config into `game-accounts.json`.
- Legacy global `accounts.json` is used only as a migration input when global `game-accounts.json` does not exist.
- Legacy `authlibInjectorServers` and `addedLittleSkin` fields are extracted into `authlib-injector-servers.json`.
- Legacy profile data from `configurations` is converted into `game-directories.json`.
- Legacy profile global settings are converted into `GameSettings.Preset` entries.
- Legacy per-instance `hmclversion.cfg` files are converted into `GameSettings.Instance` data.

## Compatibility Constraints

- Old config files should not be rewritten during migration.
- New detached files should be created only through the new storage model.
- Instance settings should resolve effective values from their selected parent preset.
- Inheritable fields should preserve explicit overrides and default to parent values when not overridden.

## Verification Focus

- Loading an old config should create detached files without losing selected account, selected directory, or selected instance state.
- Editing accounts should update `game-accounts.json`, not `settings.json`.
- Existing global `accounts.json` should migrate to global `game-accounts.json`.
- Launch, export, install, and settings UI flows should read effective `GameSettings` values.
