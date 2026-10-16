# XpTresor

**A personal XP vault for every player.** Store your experience safely, take it back whenever you need it, pay enchanting and anvils straight from it, send it to friends, let it fill up automatically, repair your gear with it, or bottle it up exactly. With a click menu and a placeable vault block.

XpTresor is made for Paper servers and has no required dependencies.

- **Author:** betamoon
- **License:** MIT, see [LICENSE](LICENSE)
- **Version:** 1.2.1 (see [CHANGELOG](CHANGELOG.md))

---

## Contents
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [How amounts work](#how-amounts-work)
- [Menu and vault block](#menu-and-vault-block)
- [Paying enchanting and anvils from the vault](#paying-enchanting-and-anvils-from-the-vault)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Languages](#languages)
- [PlaceholderAPI](#placeholderapi)
- [Vault economy](#vault-economy)
- [Switching from XP-Deposit](#switching-from-xp-deposit)
- [Data storage](#data-storage)
- [FAQ](#faq)
- [License](#license)

---

## Features

**New in 1.2.0**
- **Vault menu.** `/xpt` opens a menu: deposit and withdraw with one click, switch auto-deposit and auto-pay, repair, history and leaderboard.
- **Pay enchanting and anvils from the vault.** If your own levels are not enough, the missing levels come straight from your vault. No withdrawing first.
- **History.** `/xpt history` shows every change of your vault: deposits, withdrawals, sent and received XP, auto-pay, admin changes and more.
- **Vault block.** A craftable vault block for your base: right-click opens your vault menu, sneak + right-click deposits all your XP.

**XP management**
- **Deposit and withdraw** XP as levels, exact XP points or everything at once.
- **Fair level math.** Depositing 10 levels at level 50 takes you to level 40, and withdrawing 10 levels gives you exactly 10 more levels. Your progress bar is kept.
- **Send XP** from your vault to another player's vault, even when they are offline (optional).
- **Auto-deposit.** Choose a level; as soon as you reach it, all of your XP goes into the vault.

**Using stored XP**
- **Repair with XP.** Repair the item in your hand, or with `all` also your offhand and armor, using XP from the vault.
  - Works like vanilla Mending: Mending is required by default and you get 2 durability per XP point.
  - Supports armored elytras from ArmoredElytra.
- **Exact XP bottles.** Fill XP from your vault into bottles that release exactly the stored amount when thrown, instead of the random 3–11 XP of vanilla bottles.

**Server control**
- **Storage limits** per rank via permissions, or unlimited.
- **Keep XP on death** for players with a permission (optional).
- **Leaderboard** with pages. You can hide names from it.
- **AFK bots and NPCs** never get an account. This applies to every player entity with the `NPC` metadata, used e.g. by Citizens and most AFK-bot plugins.

**Languages and integrations**
- **10 languages built in.** Each player can automatically get the language of their Minecraft client.
- **Every message is editable,** with `&` color codes and `&#rrggbb` hex colors. Empty messages are not sent.
- **Optional integrations:**
  - **Vault:** the XP vault as the server economy.
  - **PlaceholderAPI:** placeholders for scoreboards, tab lists and holograms.
  - **ArmoredElytra:** repairs armored elytras correctly.

**Safety**
- **Migration from XP-Deposit** with a one-time import of all accounts. Old XP-Deposit bottles keep working.
- **Crash-safe.** Every change is written to disk immediately, with one small file per player.

---

## Requirements

- **Server:** Paper (developed and tested for Paper 26.x).
- **Java:** 21 or newer.
- **Optional plugins:** Vault, PlaceholderAPI, ArmoredElytra.

---

## Installation

1. Put `XpTresor-1.2.1.jar` into your `plugins` folder.
2. Restart the server. `/reload` is not supported.
3. Edit `plugins/XpTresor/config.yml` to your liking. In particular, set `language`, `limit` and `keep-xp-on-death`.
4. Run `/xpt reload` to apply the changes.

Players can use XpTresor right away, because all player features are allowed by default. See [Permissions](#permissions) to change that.

---

## Menu and vault block

**The menu** opens with `/xpt` (or `/xpt menu`):

| Button | What it does |
|---|---|
| Experience bottle (top) | Your vault, limit, how many levels you could withdraw and your own level |
| Green dyes / green block | Deposit 1, 5, 10, 20, 30 levels or everything |
| Orange dyes / orange block | Withdraw 1, 5, 10, 20, 30 levels or everything |
| Hopper | Auto-deposit: left-click on/off, right-click +5 levels, shift + right-click -5 levels |
| Enchanting table | Pay enchanting and anvils from the vault: on/off |
| Anvil | Repair: left-click the item in your hand, right-click hand, offhand and armor |
| Book | Your last 5 changes; click for the full history |
| Gold ingot | Top 5; click for the full leaderboard |

Buttons you can't use right now (not enough levels, empty vault) are greyed out. Buttons for features you have no permission for are hidden. With `menu.open-on-command: false`, `/xpt` shows the balance as text like before.

**The vault block** is crafted like this (changeable in the config). In the game, `/xpt recipe` shows it as a crafting grid:

```
Iron block   Diamond             Iron block
Diamond      Bottle o' Enchanting Diamond
Iron block   Obsidian            Iron block
```

- **Right-click:** opens your vault menu.
- **Sneak + right-click:** deposits all your XP at once.
- **Your own vault:** like an ender chest, every player uses their own vault with the same block. The block holds no XP itself.
- **Breaking:** the block drops itself again. It can't be blown up.
- The block looks like the trial chamber vault, but never acts like one, so trial keys do nothing.

---

## Paying enchanting and anvils from the vault

Minecraft only lets you click an enchantment or take an anvil result if you already have the levels. With auto-pay, XpTresor does the withdrawing for you:

- **When you open an enchanting table or anvil,** the levels you are missing are lent from your vault, so the buttons become clickable. You see them right away in your level bar.
- **When you enchant or take the anvil result,** the cost is paid from the lent XP first, then from your own.
- **When you close the menu,** everything that was not used goes back into your vault.

The result is exactly the same as withdrawing the missing levels by hand first. A cheap enchantment never costs the expensive levels at the top: when you click an offer, only what that offer needs stays lent.

- **Example:** You are level 5 and want an enchantment that needs level 8 and costs 1 level. The vault pays the XP of that 1 level, and you are still level 5 afterwards.
- **Switch it off for yourself** with `/xpt autopay off` or in the menu. Server owners can turn it off completely, or only for enchanting or the anvil, in the config.
- **What the vault paid** shows up in chat and in `/xpt history`.
- **Safe:** if you leave, die or the server stops while the table is open, the lent XP goes back into the vault first. Auto-deposit ignores lent XP.

---

## How amounts work

Every command that takes an amount understands three formats:

| Input | Meaning |
|---|---|
| `10` | 10 levels |
| `500p` or `500xp` | 500 XP points |
| `all` | everything |

The word `all` also works in the other languages: `alles`, `todo`, `tout`, `tutto`, `wszystko`, `tudo`, `все`, `hepsi`, and `max`.

**What "levels" means depends on the command:**

| Command | Levels are counted… | Example |
|---|---|---|
| `deposit` | from **your current level** downwards | At level 50, `deposit 10` → you are now level 40. |
| `withdraw` | from **your current level** upwards | At level 40, `withdraw 10` → you are now level 50. |
| `send` | from the **top of your vault** | Sends the top 10 levels of your vault. |
| `bottle`, `admin give/take/set`, limits, vault display | **from level 0** | "30 levels" = the XP needed to go from level 0 to level 30 (1,395 XP). |

Higher levels are worth more XP than lower ones, the same as in vanilla Minecraft. Because of that, 10 levels deposited at level 50 show up as more than 10 levels in the vault when the vault is counted from 0. Every message also shows the exact XP points, so nothing gets lost.

---

## Commands

**Main command:** `/xptresor`. **Aliases:** `/xpt`, `/tresor`.

Every subcommand has an English name and a German alias. All player features are allowed for everyone by default.

### Player commands

| Command | Aliases | Description | Permission |
|---|---|---|---|
| `/xpt` | | Opens the vault menu (or shows your vault as text, see `menu.open-on-command`) | `xptresor.menu` |
| `/xpt menu` | `gui`, `menü` | Opens the vault menu | `xptresor.menu` |
| `/xpt history [page]` | `verlauf`, `log` | Your last vault changes | `xptresor.history` |
| `/xpt history <player> [page]` | | Another player's history | `xptresor.history.others` |
| `/xpt autopay [on\|off]` | `autobezahlen` | Pay enchanting and anvils from the vault | `xptresor.autopay` |
| `/xpt recipe` | `receipt`, `rezept` | Shows how to craft the vault block | `xptresor.block` |
| `/xpt help` | `hilfe`, `?` | Shows all commands you are allowed to use | none |
| `/xpt deposit <amount>` | `einzahlen`, `ein`, `d` | Put XP into your vault | `xptresor.deposit` |
| `/xpt withdraw <amount>` | `abheben`, `ab`, `w` | Take XP out of your vault | `xptresor.withdraw` |
| `/xpt balance` | `kontostand`, `konto`, `bal`, `b` | Your vault, how many levels you could withdraw right now, and your limit | `xptresor.balance` |
| `/xpt balance <player>` | | Another player's vault | `xptresor.balance.others` |
| `/xpt top [page]` | `baltop`, `rangliste` | Leaderboard | `xptresor.top` |
| `/xpt send <player> <amount>` | `senden`, `give`, `pay`, `geben` | Send XP from your vault to another player's vault | `xptresor.send` |
| `/xpt auto` | `autodeposit` | Toggle auto-deposit | `xptresor.auto` |
| `/xpt auto on` / `off` | `an` / `aus` | Switch auto-deposit on or off | `xptresor.auto` |
| `/xpt auto <level>` | | Auto-deposit as soon as you reach this level (and switch it on) | `xptresor.auto` |
| `/xpt repair` | `reparieren`, `rep` | Repair the item in your hand with vault XP | `xptresor.repair` |
| `/xpt repair all` | `alle` | Repair hand, offhand and all armor | `xptresor.repair` |
| `/xpt bottle <amount> [count]` | `flasche`, `bot` | Fill vault XP into exact XP bottles | `xptresor.bottle` |

### Admin commands

These also work from the console.

| Command | Description | Permission |
|---|---|---|
| `/xpt info <player>` | Account details: vault, limit, auto-deposit, keep-XP, import status, UUID | `xptresor.admin.info` |
| `/xpt admin give <player> <amount>` | Add XP to a vault | `xptresor.admin.modify` |
| `/xpt admin take <player> <amount>` | Remove XP from a vault | `xptresor.admin.modify` |
| `/xpt admin set <player> <amount>` | Set a vault to an exact amount (`0` allowed) | `xptresor.admin.modify` |
| `/xpt admin reset <player>` | Empty a vault | `xptresor.admin.modify` |
| `/xpt admin import` | Preview the import of XP-Deposit accounts | `xptresor.admin.import` |
| `/xpt admin import confirm` | Import the XP-Deposit accounts | `xptresor.admin.import` |
| `/xpt reload` | Reload `config.yml` and all language files | `xptresor.reload` |

**Notes on admin commands:**
- Admin amounts are counted from level 0, or exact with `p`.
- German aliases: `geben`, `nehmen`, `setzen`, `zuruecksetzen` for `give`, `take`, `set`, `reset`, and `bestaetigen` for `confirm`.
- The affected player is notified if they are online.
- Admin commands work for offline players who already have an account.

**Console:** `/xpt balance <player>`, `/xpt top`, `/xpt history <player>`, `/xpt info`, `/xpt admin …` and `/xpt reload` work from the console. Everything else needs a player.

---

## Permissions

### Permission groups

| Permission | Default | Includes |
|---|---|---|
| `xptresor.player` | everyone | `deposit`, `withdraw`, `balance`, `top`, `send`, `auto`, `repair`, `bottle`, `menu`, `history`, `autopay`, `block` |
| `xptresor.admin` | op | `xptresor.player`, `balance.others`, `history.others`, `admin.info`, `admin.modify`, `admin.import`, `reload` |
| `xptresor.*` | nobody | `xptresor.admin`, `limit.unlimited`, `keepxp` |

### All permissions

| Permission | Default | Description |
|---|---|---|
| `xptresor.deposit` | everyone | `/xpt deposit` |
| `xptresor.withdraw` | everyone | `/xpt withdraw` |
| `xptresor.balance` | everyone | `/xpt balance` (own vault) and `/xpt` |
| `xptresor.balance.others` | everyone | `/xpt balance <player>` |
| `xptresor.top` | everyone | `/xpt top` |
| `xptresor.send` | everyone | `/xpt send` |
| `xptresor.auto` | everyone | `/xpt auto` and the automatic deposit itself |
| `xptresor.repair` | everyone | `/xpt repair` |
| `xptresor.bottle` | everyone | `/xpt bottle` |
| `xptresor.menu` | everyone | The vault menu (`/xpt`, `/xpt menu`) |
| `xptresor.history` | everyone | `/xpt history` (own history) |
| `xptresor.history.others` | op | `/xpt history <player>` |
| `xptresor.autopay` | everyone | Pay enchanting and anvils from the vault |
| `xptresor.block` | everyone | Use the vault block and see its recipe (`/xpt recipe`) |
| `xptresor.keepxp` | nobody | Keep all XP on death. Only works if `keep-xp-on-death: true` is set in the config. |
| `xptresor.limit.<rank>` | nobody | Higher vault limit. The ranks are set under `limit.ranks` in the config. |
| `xptresor.limit.unlimited` | op | No vault limit at all |
| `xptresor.admin.info` | op | `/xpt info` |
| `xptresor.admin.modify` | op | `/xpt admin give/take/set/reset` |
| `xptresor.admin.import` | op | `/xpt admin import` |
| `xptresor.reload` | op | `/xpt reload` |

### Example setup with LuckPerms

```
# VIPs may store up to 240 levels (limit.ranks.vip in the config)
/lp group vip permission set xptresor.limit.vip true

# Moderators keep their XP on death (requires keep-xp-on-death: true)
/lp group mod permission set xptresor.keepxp true

# Only allow viewing your own vault, not other players' vaults
/lp group default permission set xptresor.balance.others false
```

If a player has more than one rank permission, the **highest** limit counts.

---

## Configuration

`plugins/XpTresor/config.yml`, apply changes with `/xpt reload`. Exception: `vault-economy` needs a restart.

New settings are added to your existing `config.yml` automatically when you update, with their comments. Your own values are never changed.

```yaml
# Do not change - used to add new settings after plugin updates
config-version: 2

# Default language: en, de, es, fr, it, nl, pl, pt, ru, tr
language: en

# true = every player gets the messages in the language of their Minecraft client (if that language
# exists in lang/), everything else (console, item names) uses the default language above.
per-player-language: true

# XP every new account starts with (in levels from 0)
start-balance-levels: 0

deposit:
  # Smallest deposit in levels from 0 (0 = no minimum)
  min-levels: 0

# Maximum content of a vault, in levels from 0. -1 = unlimited.
# Higher limits per permission: xptresor.limit.<rank>   (the highest one counts)
# No limit at all: xptresor.limit.unlimited
limit:
  default: 120
  ranks:
    vip: 240
    mod: 360

auto-deposit:
  # Lowest level players can choose for /xpt auto <level>
  min-level: 5

# Players with the permission xptresor.keepxp keep their XP on death (nothing drops)
keep-xp-on-death: false

send:
  # Allow /xpt send to players that are offline (their account must exist)
  to-offline-players: true

# /xpt (without anything) opens the vault menu. false = /xpt shows the balance as text (menu: /xpt menu)
menu:
  open-on-command: true

# Pay enchanting and the anvil from the vault (players switch it with /xpt autopay)
auto-pay:
  enabled: true
  enchanting: true
  anvil: true
  # Is it on for players who never switched it?
  on-by-default: true

history:
  # Entries kept per player (0 = no history)
  max-entries: 50

# Right-click = vault menu, sneak + right-click = deposit all XP. Permission: xptresor.block
vault-block:
  enabled: true
  sneak-deposits-all: true
  recipe:
    enabled: true
    shape:
      - "IDI"
      - "DBD"
      - "IOI"
    ingredients:
      I: IRON_BLOCK
      D: DIAMOND
      B: EXPERIENCE_BOTTLE
      O: OBSIDIAN

repair:
  enabled: true
  # Only items with Mending can be repaired (like vanilla Mending)
  require-mending: true
  # Durability per XP point (vanilla Mending: 2)
  durability-per-xp: 2

bottle:
  enabled: true
  max-amount: 64
  # Bottles made with the old XP-Deposit plugin keep releasing their exact XP
  accept-xp-deposit-bottles: true

# Sound keys from https://minecraft.wiki/w/Sounds.json, empty = no sound
sounds:
  enabled: true
  deposit: block.amethyst_block.chime
  withdraw: entity.experience_orb.pickup
  send: entity.experience_orb.pickup
  receive: entity.player.levelup
  auto-deposit: block.amethyst_block.chime
  repair: block.anvil.use
  bottle: item.bottle.fill
  menu-click: ui.button.click
  auto-pay: block.enchantment_table.use

top:
  # Entries per page in /xpt top
  size: 10
  # Names that never show up in /xpt top
  hidden: []

# Make the vault the server economy via Vault (for shops etc.)
vault-economy:
  enabled: false
  # levels = currency is levels from 0, points = currency is XP points
  unit: levels
```

### Details

**Limits**
- The default of 120 levels equals 47,520 XP points.
- A limit of `-1`, globally or for a rank, means unlimited.

**Sending to offline players**
- The plugin can't check permissions of offline players. Because of that, the **default** limit applies to the receiver's vault.

**Auto-deposit**
- It triggers when a player **reaches** their chosen level, not on every level above it. Otherwise XP you just withdrew would go straight back into the vault.
- If a player switches auto-deposit on while already above their level, the XP is deposited right away.
- If the vault is full, the deposit is skipped. The player is told at most once per minute.

**Repair**
- `require-mending: false` lets players repair any damageable item.
- Unbreakable items can't be repaired.
- If the vault runs out partway, the repair stops and only the XP actually used is taken.

**Bottles**
- `bottle 30 5` creates 5 bottles. Each one releases the XP of level 0→30 (1,395 XP).
- The bottles stack and can be traded.
- If the inventory is full, the remaining bottles drop at the player's feet.

---

## Languages

**Built in:**

| Code | Language |
|---|---|
| `en` | English |
| `de` | German |
| `es` | Spanish |
| `fr` | French |
| `it` | Italian |
| `nl` | Dutch |
| `pl` | Polish |
| `pt` | Portuguese |
| `ru` | Russian |
| `tr` | Turkish |

**How a language is chosen:**
- `language` sets the default language. It is used for the console and item names, and for players whose client language doesn't exist.
- With `per-player-language: true`, every player automatically gets the language of their Minecraft client. A `pt_BR` client uses `messages_pt.yml`. A file matching the full code, such as `messages_pt_br.yml`, takes priority if it exists.
- If a text is missing in a file, the English text is used instead. A half-finished translation never breaks anything.

**Editing:** All files are in `plugins/XpTresor/lang/`. Change them and run `/xpt reload`. Your changes are kept on updates.

**Adding your own language:**
1. Copy `lang/messages_en.yml`, e.g. to `lang/messages_cs.yml`.
2. Translate the texts. Keep the `{placeholders}` as they are.
3. Run `/xpt reload`. The language is now available, both as `language: cs` and for players with a Czech client.

**Formatting:** Colors use `&` codes (`&a`, `&l`, …) or hex (`&#55ffaa`). A message set to `""` is not sent at all.

---

## PlaceholderAPI

These placeholders are registered automatically when PlaceholderAPI is installed.

| Placeholder | Result |
|---|---|
| `%xptresor_levels%` | Vault content in levels (from 0) |
| `%xptresor_xp%` | Vault content in XP points |
| `%xptresor_xp_formatted%` | XP points with thousands separator |
| `%xptresor_limit%` | The player's limit in levels, or "unlimited" |
| `%xptresor_auto%` | Auto-deposit on/off (localized) |
| `%xptresor_top_<n>_name%` | Name of rank `<n>` in the leaderboard |
| `%xptresor_top_<n>_levels%` | Levels of rank `<n>` |
| `%xptresor_top_<n>_xp%` | XP of rank `<n>` |

Example: `%xptresor_top_1_name%` shows the player with the fullest vault.

---

## Vault economy

With `vault-economy.enabled: true` and Vault installed, XpTresor registers itself as the economy. Shops and other plugins can then charge or pay XP directly from or into the vault.

| `unit` | 1 unit of money = |
|---|---|
| `levels` | levels counted from 0 (e.g. a price of 30 costs 1,395 XP) |
| `points` | 1 XP point |

Bank accounts are not supported. Enabling or disabling the economy needs a restart.

---

## Switching from XP-Deposit

XpTresor can take over all accounts of the old **XP-Deposit** plugin. No player loses stored XP.

1. **Back up** the folder `plugins/XP-Deposit`.
2. Install XpTresor. XP-Deposit can stay installed for now.
3. Run `/xpt admin import`. It shows how many accounts and how much XP were found. Nothing is changed yet.
4. Run `/xpt admin import confirm`. All accounts are imported.
5. Check a few players with `/xpt balance <player>`.
6. Remove XP-Deposit.

**What the import does:**
- **Balances:** Imported XP is **added** to an existing XpTresor balance. New accounts start with exactly the imported amount, without the starting balance.
- **Auto-deposit:** Settings are taken over if the player hasn't set their own yet.
- **Run it again safely:** Every account is imported only **once**, so running the import twice can't duplicate XP.
- **Old bottles:** Bottles made with XP-Deposit keep releasing their exact XP (`bottle.accept-xp-deposit-bottles`).

**Permissions to replace:**

| XP-Deposit | XpTresor |
|---|---|
| `xpd.normal.*` | `xptresor.player` |
| `xpd.admin.*` | `xptresor.admin` |
| `xpd.deposit.limit.<rank>` | `xptresor.limit.<rank>` |
| `xpd.deposit.limit.unlimited` | `xptresor.limit.unlimited` |
| `xpd.keepxp` | `xptresor.keepxp` |

**Keeping the old command:** If your players are used to `/xpd`, add this to the server's `commands.yml`:

```yaml
aliases:
  xpd:
  - "xpt $1-"
```

---

## Data storage

- **Accounts:** `plugins/XpTresor/players/<uuid>.yml`, one small file per player. It holds the name, XP, auto-deposit and auto-pay settings, the history and the import status.
- **Vault blocks** are marked in the block itself. Nothing extra is stored.
- **When it is saved:** Every change is written immediately, so a crash can't lose deposits.
- **Plugins that clean up player data,** e.g. AFK-bot plugins, can delete `XpTresor/players/{uuid}.yml`.

---

## FAQ

**Why does my vault show more levels than I deposited?**
Levels at a high level are worth more XP than levels near 0. The vault counts from level 0, like an XP bottle. The XP points are always exact; the levels are only a display. When you withdraw, you get exactly the XP back.

**Can players lose XP?**
No. Deposits, withdrawals, transfers and repairs always move the exact XP points. Every change is saved immediately. XP lent for enchanting or the anvil goes back into the vault when the menu closes, when the player leaves or dies, and when the server stops.

**Can I see where XP went?**
Yes: `/xpt history`, and for admins `/xpt history <player>`. The last 50 changes per player are kept (`history.max-entries`).

**Do AFK bots show up in the leaderboard?**
No. Players with the `NPC` metadata never get an account. You can hide any other name with `top.hidden`.

**Can I turn single features off?**
Yes, in two ways:
- **Repair and bottles:** switch them off in the config.
- **Everything else:** take away the permission. For example, set `xptresor.send` to false to disable sending.

**Does it work on Spigot or Folia?**
No. XpTresor is built for Paper.

---

## License

XpTresor is released under the **MIT License**. You may use, modify and redistribute it freely, also commercially, as long as the copyright notice and license text are included. See [LICENSE](LICENSE).

Copyright (c) 2026 betamoon
