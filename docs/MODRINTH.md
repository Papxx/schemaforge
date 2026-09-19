# Modrinth listing (P5-07)

Metadata for the Modrinth project page. The long description is the "Features" part of the README.
Upload is manual for now (no Modrinth token in CI); the jar comes from the GitHub `snapshot` release or `./gradlew build`.

| Field | Value |
|---|---|
| Name | SchemaForge |
| Slug | `schemaforge` |
| Summary | Litematica printer for Meteor Client: packet-paced placing, Baritone navigation and restock from your chests. |
| Project type | Mod |
| Loader | Fabric |
| Game versions | 26.2 |
| Environment | Client: required · Server: unsupported |
| Categories | Utility, Technology |
| License | GPL-3.0-only |
| Source | https://github.com/Papxx/schemaforge |
| Issues | https://github.com/Papxx/schemaforge/issues |
| Version number | from `gradle/libs.versions.toml` → `mod-version` |
| Version type | Beta, until the in-game checks in `docs/TASKS.md` (Backlog) are done |

## Dependencies

| Project | Type | Note |
|---|---|---|
| Meteor Client (26.2 snapshot) | required | not on Modrinth, link to https://meteorclient.com |
| Litematica | optional | needed for printing; without it the addon loads and says so |
| MaLiLib | optional | comes with Litematica |
| Baritone (Meteor fork 26.2) | optional | needed to walk between clusters and containers; without it only blocks within reach are placed |

## Changelog for the first release

- Printer: work clusters, packet budget, additive-only by default, support-aware order
- Navigation with Baritone, safety pauses, resume after disconnect, progress HUD
- Container index, `.sf scan`, automatic restock, shulkers from the inventory
- `.sf undo`, temporary supports, fluids, rails, pacing profiles, accurate placement V2/V3
