# Changelog

All notable changes to XpTresor.

## [1.2.0] - 2026-10-15

### Added

- **Vault menu.** `/xpt` now opens a menu (also `/xpt menu`):
  - Deposit or withdraw 1, 5, 10, 20, 30 levels or everything with one click. Buttons you can't use right now are greyed out.
  - Switch auto-deposit on or off and change its level (right-click +5, shift + right-click -5).
  - Switch auto-pay on or off.
  - Repair the item in your hand, or with a right-click your hand, offhand and armor.
  - See your last 5 changes and the top 5 players right in the menu.
- **Pay enchanting and anvils from the vault (auto-pay).** If your own levels are not enough at the enchanting table or anvil, the missing levels are taken from your vault.
  - The result is the same as withdrawing them by hand first.
  - A cheap enchantment never costs the expensive levels at the top.
  - Unused XP goes back into the vault when the menu closes, when you leave or die, and when the server stops.
  - Even after a crash, lent XP that was not saved with the player is returned to the vault on the next start.
  - Players switch it with `/xpt autopay [on|off]` or in the menu.
  - Server owners can turn it off completely, or only for enchanting or the anvil.
- **History.** `/xpt history [page]` lists the last changes of your vault with date, amount and reason:
  - deposits, withdrawals, auto-deposit
  - sent and received XP, including who sent or received it
  - auto-pay at the enchanting table and anvil
  - repairs and XP bottles
  - shop / economy payments
  - admin changes, including which admin made them

  Admins can view anyone's history with `/xpt history <player>`. 50 entries per player are kept by default.
- **Vault block.** A craftable vault block (recipe in the config).
  - Right-click it to open your vault menu.
  - Sneak + right-click it to deposit all your XP.
  - Every player uses their own vault with the same block, like an ender chest.
  - The block drops itself when broken, can't be blown up, and never acts like a trial chamber vault.
- **New permissions** (all in `xptresor.player` and allowed for everyone by default):
  - `xptresor.menu`
  - `xptresor.history`
  - `xptresor.autopay`
  - `xptresor.block`
  - `xptresor.history.others`, which is op only and part of `xptresor.admin`.
- **New settings:** `menu`, `auto-pay`, `history`, `vault-block`, plus the sounds `menu-click` and `auto-pay`.
- **All new texts** in all 10 languages.

### Changed

- `/xpt` without anything opens the menu. Set `menu.open-on-command: false` to keep the old text output.
- **Config updates itself.** Existing `config.yml` files get the new settings, with their comments, automatically on the first start. Your own values are not changed.

### Upgrading from 1.1.x

Replace the jar and restart. Nothing else is needed:
- Accounts, settings and language files stay as they are.
- New texts that are missing in your edited language files fall back to the built-in translation.

[1.2.0]: https://github.com/betamoon1/XpTresor/releases/tag/v1.2.0
