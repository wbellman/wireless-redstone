# Contributing

Thanks for your interest in Wireless Redstone!

## Reporting bugs

Open an [issue](https://github.com/wbellman/wireless-redstone/issues) with:

- the mod, Minecraft, Fabric Loader and Fabric API versions;
- whether it happened in single player or on a server;
- what you built (a screenshot helps a lot — press F3 and look at the panel to include its
  `mode`, `power` and `lit` values), what you expected, and what happened instead;
- the log (`logs/latest.log`) or crash report, if there is one.

## Suggesting changes

Ideas are welcome as issues. For anything larger than a small fix, please open an issue to talk
it through before writing code, so your time isn't wasted on something that doesn't fit.

## Pull requests

1. See [docs/development.md](docs/development.md) for building and the project layout.
2. Keep changes focused: one fix or feature per pull request.
3. Add or update a game test in `src/gametest` for any behavior change, and make sure
   `./gradlew build` passes (it runs the headless tests).
4. Edit the generator scripts in `tools/` rather than the generated model JSON.
5. Update [CHANGELOG.md](CHANGELOG.md) under an "Unreleased" heading.

Code style: tabs for indentation, Mojang names (Minecraft 26.x is unobfuscated), and short
comments that explain *why* where it isn't obvious.

By submitting a contribution you agree that it is licensed under the project's license,
LGPL-3.0-or-later.
