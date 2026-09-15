# Koi

A custom fishing plugin for **Folia + Paper 1.21.11**, built in strict Java 21 OOP. Replaces
vanilla fishing with a click-reveal reel-in minigame, rarity-tiered fish/treasure/junk, rod
tiers, bait, a personal collection book, and server-wide tournaments.

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

## Requirements

- Java 21
- [CommandAPI](https://commandapi.jorel.dev/) installed as a separate plugin on the server
  (Koi depends on it but doesn't shade it)
- A Folia or Paper 1.21.11 server

## Building

The Maven wrapper is checked in, so no local Maven install is required:

```
./mvnw package        # macOS/Linux
mvnw.cmd package       # Windows
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

## Configuration

- `config.yml` — per-world enable/disable, default catch sound, vanilla-loot toggle, reel
  minigame tick rate.
- `fish-pool.yml` — every catchable species (fish, treasure, junk): rarity, material,
  CustomModelData, drop weight, category, optional catch sound and favored bait/biomes. Editable
  in-game via `/koi config`, or by hand followed by `/koi reload`.

## Architecture

Strict OOP, one concern per package:

- `fishing` / `fishing.minigame` — domain model, catch pool, and the reel-in minigame
- `item` — rod and bait items
- `tournament` — timed server-wide events
- `command` — `/koi` and `/koidex` registration (CommandAPI)
- `listener` — Bukkit event hooks
- `menu` — InvUI GUIs (fish editor, Koi-dex)
- `data` — SQLite persistence (catch history, aggregate stats)
- `config` — `config.yml` / `fish-pool.yml` loading

See `CLAUDE.md` for the full guidance this project was built against.
