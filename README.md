# 🎣 Koi

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?logo=minecraft&logoColor=white)
![Folia](https://img.shields.io/badge/Folia-supported-brightgreen)
![CommandAPI](https://img.shields.io/badge/requires-CommandAPI-lightgrey)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-inactive)

A custom fishing plugin for **Folia + Paper 1.21.11**, built in strict Java 21 OOP. Replaces
vanilla fishing with a click-reveal reel-in minigame, rarity-tiered fish/treasure/junk, rod
tiers, bait, a personal collection book, and server-wide tournaments.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Building](#building)
- [Commands](#commands)
- [Configuration](#configuration)
- [Architecture](#architecture)

## Features

- **Reel-in minigame** — after a bite, click to reveal a row of bars in the catch's true rarity
  color, one bar at a time. Landing a rarer catch means clicking through every lower tier's bars
  first (Common is the shortest, Mythic the longest), so there's no fixed click count to
  memorize — the exact number is rolled per catch.
- **Rarity tiers** — Common → Uncommon → Rare → Epic → Legendary → Mythic, each with its own
  color and pool weight.
- **Rod tiers** — Wood → Iron → Diamond → Koi, craftable upgrades. Better rods hook faster
  (shorter wait-for-a-bite window) and skew the catch roll toward rarer fish.
- **Bait** — craftable, held in the off-hand, consumed one per cast, shifts the roll toward
  favored rarities.
- **Treasure & junk** — non-fish catches through the same pool/minigame. Vanilla items (saddles,
  name tags, enchanted books) drop as-is and are off by default (toggle in `config.yml`);
  Koi-flavored custom items (message in a bottle, waterlogged boot, rusty trinket) always drop.
- **Sound & particle polish** — bite/click/catch sound cues and a rarity-colored particle burst
  on every catch, scaled up for rarer catches. Per-species catch sounds are configurable with a
  server-wide fallback.
- **`/koidex`** — a personal collection-book GUI: every known species, shown as the real item
  once you've caught it and as a "???" placeholder until then.
- **`/koi stats`** — total catches and rarest catch, for yourself or (with permission) another
  player.
- **Fishing tournaments** — `/koi tournament` runs a timed, server-wide best-catch event with an
  admin-set prize handed to the winner automatically.
- **Live fish pool editor** — `/koi config` opens an in-game GUI to add, remove, and retune every
  species (fish, treasure, and junk) without touching a file.

> [!NOTE]
> Custom fish/treasure/junk beyond vanilla's cod/salmon/pufferfish/tropical fish reuse those
> materials with distinct CustomModelData values. Actual resource-pack textures are a separate
> art task, not part of this plugin.

## Requirements

- Java 21
- Maven
- [CommandAPI](https://commandapi.jorel.dev/) installed as a separate plugin on the server
  (Koi depends on it but doesn't shade it)
- A Folia or Paper 1.21.11 server

## Building

Requires Maven installed locally:

```bash
mvn package
```

The shaded jar lands at `target/Koi-1.0.0.jar`. Drop it into the server's `plugins/` folder
alongside CommandAPI.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/koi give <player> <rod\|bait\|fish> <id> [amount]` | Give an item | `koi.admin` |
| `/koi reload` | Reload `config.yml` and `fish-pool.yml` | `koi.admin` |
| `/koi world enable\|disable [world]` | Toggle fishing in a world | `koi.admin` |
| `/koi config` | Open the live fish pool editor GUI | `koi.admin` |
| `/koi test tier <rarity> [player]` / `/koi test clear [player]` | Force or clear a player's next catch rarity, for testing | `koi.admin` |
| `/koi stats [player]` | Show catch stats | none (self) / `koi.admin` (others) |
| `/koi tournament setprize\|start\|stop\|status` | Run a tournament | `koi.admin` (`status` is open to all) |
| `/koidex` | Open your personal collection book | none |

<details>
<summary><strong>Rod tiers &amp; bait reference</strong></summary>

| Rod | Hook window | Rarity boost |
|---|---|---|
| Wood | 5–30s | none |
| Iron | 4–22s | low |
| Diamond | 2.75–14s | medium |
| Koi | 1.5–7.5s | high |

| Bait | Favors | Craft |
|---|---|---|
| Worm Bait | Common / Uncommon | 8× String + Nether Wart |
| Shrimp Bait | Rare / Epic | 8× Prismarine Shard + Nether Wart |
| Glowing Lure | Legendary / Mythic | 8× Glow Ink Sac + Nether Wart |

</details>

## Configuration

- `config.yml` — per-world enable/disable, default catch sound, vanilla-loot toggle, reel
  minigame tick rate.
- `fish-pool.yml` — every catchable species (fish, treasure, junk): rarity, material,
  CustomModelData, drop weight, category, optional catch sound and favored bait/biomes. Editable
  in-game via `/koi config`, or by hand followed by `/koi reload`.

## Architecture

Strict OOP, one concern per package:

| Package | Responsibility |
|---|---|
| `fishing` / `fishing.minigame` | Domain model, catch pool, and the reel-in minigame |
| `item` | Rod and bait items |
| `tournament` | Timed server-wide events |
| `command` | `/koi` and `/koidex` registration (CommandAPI) |
| `listener` | Bukkit event hooks |
| `menu` | InvUI GUIs (fish editor, Koi-dex) |
| `data` | SQLite persistence (catch history, aggregate stats) |
| `config` | `config.yml` / `fish-pool.yml` loading |

---

See [`CLAUDE.md`](CLAUDE.md) for the full guidance this project was built against.
