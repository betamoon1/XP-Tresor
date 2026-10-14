# XpTresor – your personal XP vault

**Never lose your hard-earned experience again.** XpTresor gives every player a personal XP vault. Deposit your levels before a risky fight, withdraw them for enchanting, send them to friends, or let the vault fill up automatically while you farm.

Made for **Paper**, no required dependencies, available in **10 languages**.

---

## ✨ Features

- 🏦 **Deposit and withdraw XP:** in levels, exact XP points or everything at once.
  - The level math is fair: depositing 10 levels at level 50 really takes you to level 40.
- 📨 **Send XP:** to another player's vault, even while they are offline.
- 🤖 **Auto-deposit:** pick a level, and once you reach it your XP goes into the vault automatically. Perfect for XP farms.
- 🔧 **Repair with XP:** fix your Mending gear (hand, offhand and armor) with stored XP, exactly like vanilla Mending.
- 🧪 **Exact XP bottles:** bottle up stored XP. Each bottle releases exactly the amount you put in, not the random 3–11 XP of vanilla bottles.
- 🏆 **Leaderboard:** who has the fullest vault?
- 🔒 **Limits per rank:** set different vault limits with permissions, or allow unlimited storage.
- 💀 **Keep XP on death:** optional, per permission.
- 🌍 **10 languages:**
  - English, German, Spanish, French, Italian, Dutch, Polish, Portuguese, Russian, Turkish.
  - Every player automatically gets their client's language.
  - Every message is editable, and you can add your own languages.
- 🤝 **Integrations:** Vault economy, PlaceholderAPI placeholders and ArmoredElytra support.
- 🚫 **No bots:** AFK bots and NPCs never get an account or show up in the leaderboard.
- 💾 **Crash-safe:** every change is saved immediately.
- 🔄 **Switch from XP-Deposit:** a one-time import of all old accounts, and old bottles keep working.

---

## 💬 Commands

`/xpt` (also `/xptresor`, `/tresor`)

| Command | What it does |
|---|---|
| `/xpt deposit <amount>` | Put XP into your vault |
| `/xpt withdraw <amount>` | Take XP out |
| `/xpt balance [player]` | What's in the vault? |
| `/xpt send <player> <amount>` | Send XP to someone's vault |
| `/xpt auto [on/off/<level>]` | Auto-deposit |
| `/xpt repair [all]` | Repair Mending items with stored XP |
| `/xpt bottle <levels> [count]` | Fill XP into exact bottles |
| `/xpt top` | Leaderboard |

**Amounts:** `10` = 10 levels, `500p` = 500 XP points, `all` = everything.

**Admin commands:**
- `/xpt info <player>`
- `/xpt admin give|take|set|reset <player> [amount]`
- `/xpt admin import`
- `/xpt reload`

---

## 🔑 Permissions

| Permission | Default | |
|---|---|---|
| `xptresor.player` | everyone | All player features |
| `xptresor.admin` | op | All admin commands |
| `xptresor.limit.<rank>` | – | Higher vault limit (ranks in the config) |
| `xptresor.limit.unlimited` | op | No vault limit |
| `xptresor.keepxp` | – | Keep XP on death (if enabled in the config) |

Every feature also has its own permission, e.g. `xptresor.send` or `xptresor.repair`. The full list is in the README.

---

## 📊 Placeholders (PlaceholderAPI)

- **Your vault:** `%xptresor_levels%` · `%xptresor_xp%` · `%xptresor_xp_formatted%` · `%xptresor_limit%` · `%xptresor_auto%`
- **Leaderboard:** `%xptresor_top_<n>_name%` · `%xptresor_top_<n>_levels%` · `%xptresor_top_<n>_xp%`

---

## 📋 Requirements

- **Server:** Paper (developed for 26.x).
- **Java:** 21 or newer.
- **Optional:** Vault, PlaceholderAPI, ArmoredElytra.

---

## 📄 License

MIT. Free to use, modify and share. Made by **betamoon**.
