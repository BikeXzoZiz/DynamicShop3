# DynamicShop3 (modernized fork)

Full GUI admin shop for Minecraft servers with **dynamic prices** — the more an
item is bought, the more expensive it gets; selling floods the stock and lowers
the price. Prices are driven by `median` (reference demand) vs. `stock`
(supply), with per-shop tax, discounts, bulk slippage and multiple currencies
(Vault, Jobs points, PlayerPoints, XP).

This fork **continues development** of the original project, which has been
inactive since mid-2024, and modernizes it for current servers:

- **Minecraft 1.21+** (`api-version: '1.21'`, compiled against Paper API 1.21.x)
- **Java 21** build (JDK 21 required to compile and run the server)
- JUnit 5 golden tests locking down the price engine, GitHub Actions CI
- Dependency and build refresh (bStats 3, opencsv 5.9, commons-io 2.16, Maven Wrapper)

## Building

```bash
./mvnw clean package     # Linux/macOS
mvnw.cmd clean package   # Windows
```

The plugin jar is written to `target/DynamicShop-<version>.jar`.
Continuous integration builds every push — see `.github/workflows/build.yml`.

## Requirements

| Dependency | Scope |
|---|---|
| Paper / Spigot 1.21+ | server |
| Vault | required |
| PlaceholderAPI, Jobs, PlayerPoints | optional hooks |

## Commands

| Command | Description |
|---|---|
| `/ds` (alias `/DynamicShop`) | main admin command |
| `/shop` | open the shop menu |
| `/sell` | quick-sell held items |

## Credits & license

- Based on **DynamicShop3 by [7sat](https://github.com/7sat/DynamicShop3)**
  ([free version on SpigotMC](https://www.spigotmc.org/resources/65603/)) — thank you for the great plugin.
- MIT License — original copyright (c) 2023 7sat, see [LICENSE](LICENSE) and [NOTICE](NOTICE).
- Contributions from the original project's authors are preserved in the git history.
