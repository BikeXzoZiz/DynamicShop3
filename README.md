<div align="center">

# DynamicShop3

**A GUI admin shop plugin for Paper 1.21+ with dynamic pricing**

Buy more, pay more. Sell more, earn less. Prices are driven by supply and demand —
no config grind, no flat price lists.

[![CI](https://github.com/BikeXzoZiz/DynamicShop3/actions/workflows/build.yml/badge.svg)](https://github.com/BikeXzoZiz/DynamicShop3/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/BikeXzoZiz/DynamicShop3)](https://github.com/BikeXzoZiz/DynamicShop3/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-darkgreen)
![Java](https://img.shields.io/badge/Java-21-orange)

[Features](#features) · [Installation](#installation) · [Commands](#commands) · [Configuration](#configuration) · [Developer API](#developer-api) · [Roadmap](#roadmap) · [Credits](#credits--license)

</div>

---

## About

DynamicShop3 is a full GUI shop plugin for Minecraft servers. This repository is the
**continued, modernized fork** of the original
[DynamicShop3 by 7sat](https://github.com/7sat/DynamicShop3) ([SpigotMC](https://www.spigotmc.org/resources/65603/)),
which has been inactive since mid-2024.

The fork keeps the original behaviour — locked down by golden tests — and modernizes
the platform it runs on:

- **Paper 1.21.x** (`api-version: '1.21'`), **Java 21** only
- Fixed for current Paper builds (plugin loading, first-boot data folder, command dispatch)
- JUnit 5 **golden tests** locking the pricing engine and bundled resources (23 tests)
- Continuous integration on every push (GitHub Actions, JDK 21)
- Internal cleanup: services instead of god classes, bundled data moved out of code

> Verified on **Paper 1.21.11** with Vault + EssentialsX.

## Features

**Dynamic pricing**
- Price = `median × value / stock` — every purchase raises the next price (slippage), every sale lowers it
- Per-item min/max price bounds, discounts, fixed prices, separate buy/sell values
- Per-shop sales tax, integer-only rounding, bulk totals with unit-by-unit repricing

**Shops**
- Multi-page GUI shops with decorations, per-shop balance (money-based stock), linked shops
- Shop flags: sign shops, local shops (position-bound), delivery charges, business hours, hidden pricing/stock
- 7-slot **rotations** with shared stock data across rotation sets
- Stock fluctuation and stabilization schedules (per-shop intervals and strength)
- Copy / rename / merge shops, per-slot run-on-trade commands

**Economy**
- Multiple currencies: **Vault**, **XP**, **PlayerPoints**, **Jobs points** — mixable per shop
- Per-player trade limits (per item, per interval), quick sell (`/sell`, shift-click)
- Best-price shop search across all accessible shops

**Integrations**
- Vault (required), PlaceholderAPI, Jobs Reborn, PlayerPoints
- Localized item names, hex color codes, ko-KR / en-US messages (user-editable)

## Requirements

| Dependency | Scope |
|---|---|
| Paper (or Spigot) **1.21+** | server |
| **Java 21+** | runtime |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | required |
| An economy provider (e.g. [EssentialsX](https://essentialsx.net/)) | required for Vault money |
| PlaceholderAPI, Jobs, PlayerPoints | optional hooks |

## Installation

1. Drop `DynamicShop-<version>.jar` into your server's `plugins/` folder (alongside Vault and an economy plugin).
2. Start the server — a `SampleShop` is created on first boot.
3. Open it with `/ds openshop SampleShop`, or give yourself `op` and explore `/ds` and `/shop`.

## Commands

| Command | Description |
|---|---|
| `/ds` | main admin command |
| `/shop [shopname]` | open the start page / a shop |
| `/sell` | quick-sell held items |

<details>
<summary><strong>Admin subcommands</strong> (click to expand)</summary>

| Command | Description |
|---|---|
| `/ds createshop <name> [permission]` | create a shop |
| `/ds deleteshop <name>` | delete a shop |
| `/ds renameshop <old> <new>` | rename a shop (rotation + user data follow) |
| `/ds copyshop <old> <new>` | copy a shop |
| `/ds mergeshop <target> <source>` | merge source into target |
| `/ds openshop <name> [player]` | open a shop GUI |
| `/ds setdefaultshop <name>` | default shop for `/shop` |
| `/ds settax <percent>` | global default sales tax |
| `/ds reload` | reload all configs |
| `/ds deleteuser <days>` | purge inactive user data |
| `/ds qsell` | open the quick sell GUI |
| `/shop edit <name>` | shop settings GUI |
| `/shop add <name>` / `/shop addhand <name>` | add items to a shop |

</details>

## Configuration

Everything is plain YAML, generated on first boot and safe to edit live (`/ds reload`):

| File / folder | Purpose |
|---|---|
| `config.yml` | language, tax, delivery charge scale, command/UI toggles |
| `Shop/*.yml` | one file per shop: items, prices, stock, flags, balance |
| `Lang_V3_ko-KR.yml` / `Lang_V3_en-US.yml` | all player-facing messages |
| `Worth_V2.yml` | price recommendation table (`/shop … recommend`) |
| `Startpage.yml` | start page GUI buttons |
| `Sound.yml`, `Layout.yml`, `Sign.yml` | sounds, GUI layout, sign shops |
| `User.yml` | per-player data (join date, trade limits) |
| `Rotation/<shop>/` | rotation files (per shop) |
| `Shop_Backup/`, `Log/` | automatic backups, trade logs |

## Building from source

```bash
./mvnw clean package      # Linux/macOS
mvnw.cmd clean package    # Windows
```

Requires JDK 21. The plugin jar is written to `target/DynamicShop-<version>.jar`.

```bash
./mvnw test               # run the golden test suite
```

## Developer API

Other plugins can integrate through the static `DynaShopAPI` facade:

```java
// query
double   buy  = DynaShopAPI.getBuyPrice(shop, itemStack);
double   sell = DynaShopAPI.getSellPrice(shop, itemStack);
int      stock = DynaShopAPI.getStock(shop, itemStack);
String   currency = DynaShopAPI.GetShopCurrency(shop);
ArrayList<String> shops = DynaShopAPI.getShops();

// actions
DynaShopAPI.openShopGui(player, shopName, page);
DynaShopAPI.QuickSell(player, itemStack);
```

> The API surface is being reorganized into interface ports so addons can be
> developed against a stable contract — see the roadmap.

## Roadmap

- [ ] Fix selling potions (#92)
- [ ] Bulk buy / sell from the trade GUI (#90)
- [ ] Global stock reset command (#94)
- [ ] Global sales limit (#87)
- [ ] Folia support (#95)
- [ ] Customizable GUI click buttons (#96)
- [ ] Stable API ports for addon developers
- Later: DiscordSRV log hook (#88), Slimefun support (#84)

## Credits & License

- Based on **DynamicShop3 by [7sat](https://github.com/7sat/DynamicShop3)**
  ([free version on SpigotMC](https://www.spigotmc.org/resources/65603/)) — thank you for the great plugin.
- Original authors preserved from the upstream project:
  sat7, jpenilla, Multitallented, Chris6ix, KevinDaGame, FlorianKoerner, Kapitowa, LincolnJota.
- MIT License — original copyright (c) 2023 7sat, continued development (c) 2026 Teerachot.
  See [LICENSE](LICENSE) and [NOTICE](NOTICE).
