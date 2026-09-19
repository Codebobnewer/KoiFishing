# 🎣 Koi

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?logo=minecraft&logoColor=white)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen)
![CommandAPI](https://img.shields.io/badge/requires-CommandAPI-lightgrey)
![Vulcan](https://img.shields.io/badge/requires-Vulcan-lightgrey)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-inactive)

A custom fishing plugin for **Folia 1.21.11**, built in strict Java 21 OOP. Replaces
vanilla fishing with a click-reveal reel-in minigame, rarity-tiered fish/treasure/junk, rod
tiers, bait, rare mob "sea creature" encounters, a personal collection book, and server-wide
tournaments — all authored as **Vulcan** items, so every catchable's appearance and rarity live
in Vulcan's own item editor, not hardcoded here.

## Contents

- [Features](#features)
- [How content is authored](#how-content-is-authored)
- [Requirements](#requirements)
- [Building](#building)
- [Commands](#commands)
- [Configuration](#configuration)
- [Architecture](#architecture)

## Features

- **Reel-in minigame** — after a bite, click to reveal a row of bars in the catch's true rarity
  color, one bar at a time. Landing a rarer catch means clicking through every lower tier's bars
  first (Common is the shortest, Mythic the longest), so there's no fixed click count to
  memorize — the exact number is rolled per catch. A session correctly ends early if you die, the
  hook gets invalidated (out of range, a damage-snapped line, etc.), or you swap away from your
  rod, including via inventory click/drag, not just a hotbar-slot change.
- **Rarity tiers** — Common → Uncommon → Rare → Epic → Legendary → Mythic, each with its own
  color and reel-in bar count. A catchable's rarity comes from its Vulcan item definition (set
  once there with `/v item edit <id>`, mapped 1:1 by name), not duplicated in Koi's own config.
- **Rod tiers** — admin-defined, config-driven, no defaults shipped. Each tier's rarity boost and
  hook-speed window are set in `rod-tiers.yml`; an optional `upgrades-from`/`upgrade-material`
  pair auto-registers a crafting recipe. Fishing without a genuine Vulcan/Koi rod in hand (a
  vanilla rod, or nothing) doesn't trigger any of this — vanilla fishing behaves normally.
- **Bait** — admin-defined, held in the off-hand, consumed one per cast, skews the roll toward
  favored rarities by a configurable potency. YAML-only (`bait-types.yml`); no in-game editor GUI.
- **Treasure & junk** — non-fish catches through the same pool/minigame. Vanilla items (saddles,
  name tags, enchanted books) drop as-is and are off by default (toggle in `config.yml`).
- **Sea creature encounters** — a bite can instead be a rare mob encounter (`sea-creatures.yml`,
  chance configurable, 0 by default). Reeled in through the exact same minigame, then a real mob
  spawns — one of **[Tyche](#requirements)**'s admin-authored mobs if Tyche is installed and the
  configured mob id still exists, otherwise a vanilla `EntityType` fallback — that must be killed
  within a despawn window before it escapes to actually earn the catch.
- **Sound & particle polish** — bite/click/catch sound cues and a rarity-colored particle burst
  on every catch, scaled up for rarer catches. Per-species catch sounds are configurable with a
  server-wide fallback.
- **`/koidex`** — a personal collection-book GUI with a Fish tab and a Sea Creatures tab: every
  known entry shown as its real item/icon once discovered, and as a "???" placeholder until then.
- **`/koi stats`** — total catches, sea creatures caught/killed, and rarest catch, for yourself or
  (with permission) another player.
- **Fishing tournaments** — `/koi tournament` runs a timed, server-wide best-catch event with an
  admin-set prize handed to the winner automatically. Can also be scheduled for a specific future
  date/time or set to repeat hourly/daily/weekly; with **[Chronos](#requirements)** installed,
  scheduled/recurring tournaments are advertised on its in-game calendar in advance.
- **Live catalog editors** — `/koi config` opens an admin GUI hub linking to paged editors for
  fish species, rod tiers, and sea creatures: click to cycle category/rarity, bump drop-weight or
  stats, or remove an entry, plus a guided chat wizard to add new ones — all saved straight back
  to the matching YAML file. Bait types are YAML-only, no editor GUI yet.

## How content is authored

Koi ships with **no fish, rods, bait, or sea creatures defined** — `fish-pool.yml`,
`rod-tiers.yml`, `bait-types.yml`, and `sea-creatures.yml` are all empty by default. Every
catchable is an id shared between Koi and Vulcan:

1. Author the item in Vulcan: `/v item create <id>` (appearance, and for fish/rods/bait, rarity).
2. Give it Koi-side gameplay data — either by hand in the matching YAML file, or in-game via
   `/koi config`'s fish/rod-tier/sea-creature editors (bait is YAML-only for now).
3. `/koi reload` if you edited a file directly instead of using a GUI.

An id in Koi's YAML with no matching Vulcan item still loads, but isn't actually catchable/
craftable/giveable until the Vulcan item exists — editor menus flag these with a red barrier icon.
Sea creatures are the one exception: their appearance/loot come entirely from a Tyche mob, not
Vulcan, since they're never held as an item.

## Requirements

- Java 21
- Maven
- A Folia or Paper 1.21.11 server
- [CommandAPI](https://commandapi.jorel.dev/) installed as a separate plugin on the server
  (hard `depend`, not shaded)
- Vulcan installed as a separate plugin on the server (hard `depend`, not shaded) — owns every
  catchable/rod/bait item's appearance and rarity. Not published to a remote repo; build and
  install it locally first (see [Building](#building))
- *Optional:* Chronos, installed as a separate plugin — tournament scheduling degrades gracefully
  without it (no calendar advertising, everything else still works)
- *Optional:* Tyche, installed as a separate plugin — sea creatures fall back to a vanilla
  `EntityType` without it

## Building

Vulcan and Tyche aren't published to a remote repository — install them to your local
`~/.m2` first.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/koi give <player> <rod\|bait\|fish> <id> [amount]` | Give an item | `koi.admin` |
| `/koi reload` | Reload `config.yml`, `rod-tiers.yml`, `bait-types.yml`, `sea-creatures.yml`, and `fish-pool.yml` | `koi.admin` |
| `/koi world enable\|disable [world]` | Toggle fishing in a world | `koi.admin` |
| `/koi config` | Open the admin hub GUI (fish/rod-tier/sea-creature editors) | `koi.admin` |
| `/koi test tier <rarity> [player]` | Force a player's next catch rarity | `koi.admin` |
| `/koi test seacreature [player]` | Force a player's next bite to be a sea creature encounter | `koi.admin` |
| `/koi test clear [player]` | Clear forced tier/sea-creature overrides | `koi.admin` |
| `/koi stats [player]` | Show catch stats | none (self) / `koi.admin` (others) |
| `/koi tournament setprize` | Set the prize to the item in your main hand | `koi.admin` |
| `/koi tournament start <minutes>` | Start a tournament immediately | `koi.admin` |
| `/koi tournament stop` | Stop the running tournament | `koi.admin` |
| `/koi tournament status` | Show the running/scheduled/recurring tournament state | none |
| `/koi tournament schedule <year> <month> <day> <hour> <minute> <durationMinutes>` | Schedule a one-off future tournament | `koi.admin` |
| `/koi tournament cancelschedule` | Cancel the scheduled tournament | `koi.admin` |
| `/koi tournament repeat <hourly\|daily\|weekly> <durationMinutes>` | Start a recurring tournament schedule | `koi.admin` |
| `/koi tournament stoprepeat` | Cancel the recurring tournament schedule | `koi.admin` |
| `/koidex` | Open your personal collection book | none |

## Configuration

- `config.yml` — database file name, per-world enable/disable, default catch sound,
  vanilla-loot toggle, sea-creature-encounter chance, reel minigame tick rate.
- `fish-pool.yml` — every catchable species (fish, treasure, junk) by Vulcan item id: drop
  weight, category, optional catch sound, favored bait/biomes, biome whitelist. Empty by
  default. Editable in-game via `/koi config`, or by hand followed by `/koi reload`.
- `rod-tiers.yml` — every rod tier by Vulcan item id: rarity boost, hook-speed window, optional
  upgrade recipe. Empty by default.
- `bait-types.yml` — every bait type by Vulcan item id: craft material, potency, favored
  rarities. Empty by default. YAML-only, no in-game editor.
- `sea-creatures.yml` — every sea creature encounter by free-form id: Tyche mob id, vanilla
  fallback entity type, rarity, max health, spawn weight, despawn seconds. Empty by default.

## Architecture

Strict OOP, one concern per package:

| Package | Responsibility |
|---|---|
| `fishing` / `fishing.minigame` | Domain model, catch pool, sea creature pool, Tyche integration, and the reel-in minigame |
| `item` | Rod and bait items/tiers |
| `tournament` | Timed server-wide events and Chronos calendar integration |
| `command` | `/koi` and `/koidex` registration (CommandAPI) |
| `listener` | Bukkit event hooks (fishing flow, rod/session interruption, sea creature kills) |
| `menu` | InvUI GUIs (admin catalog editors, Koi-dex) |
| `data` | SQLite persistence (catch history, aggregate stats) |
| `config` | YAML config loading (`config.yml`, `fish-pool.yml`, `rod-tiers.yml`, `bait-types.yml`, `sea-creatures.yml`) |
| `util` | Small shared helpers (weighted random rolls) |

---
