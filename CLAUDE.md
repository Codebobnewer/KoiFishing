# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Koi: a standalone Minecraft plugin/server project targeting **Folia + Paper compatibility**
(1.21.11). A fishing plugin — mechanics, fish pool, and progression are not yet designed.
Currently just an outline/skeleton (stub classes with `TODO`s, no real logic), created on
request as scaffolding to build out later.

## Requirements

- Java 21
- Maven (build tool)
- Folia compatibility (avoid APIs that break under Folia's regionized threading model —
  e.g. no global `BukkitScheduler` assumptions; use the scheduler abstraction below)

## Dependencies

- InvUI — inventory GUI framework for stats/leaderboard menus
- CommandAPI — command registration/handling
- UniversalScheduler — Folia/Paper-safe task scheduling (always use this instead of raw
  Bukkit scheduler calls)
- Lombok — reduce boilerplate (getters/setters/builders/etc.)
- Adventure text-minimessage — command feedback text should use MiniMessage via Adventure,
  not legacy `§` color codes

If a database is needed:

- HikariCP — connection pooling
- sqlite-jdbc — SQLite driver

## Architecture

- Strict OOP: favor clear class hierarchies, encapsulation, and single-responsibility
  classes over procedural/utility-dump style code.

## Author preferences

- Refer to the user as goga221.

## Gotchas

- Koi's recurring tournament schedule (`tournament.TournamentManager.startRepeating`/`checkRecurring`) needs to advertise its *next* occurrence on Chronos's calendar in advance, not just register a "happening now" entry once the tournament actually starts. Doing that requires converting a raw absolute game-time `long` (the computed next-trigger instant) back into a year/month/day/hour/minute Chronos can register. `ChronosAPI` didn't expose that conversion until Chronos added `getDateTimeAt(long totalGameSeconds)` for exactly this — `ChronosIntegration.registerAt(...)` wraps it. If Koi is ever rebuilt against an older Chronos artifact that lacks `getDateTimeAt`, this will fail to compile; that method needs to exist on the installed Chronos.
