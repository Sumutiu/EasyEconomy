# 💎 EasyEconomy – Because emeralds are overrated!

**EasyEconomy** is a lightweight and fully server-side **Fabric** mod that brings a simple, diamond-based economy to your Minecraft world.  
Deposit, withdraw, trade, and run your own shop — all powered by the sparkle of diamonds.  

Perfect for survival servers, SMPs, and economy-driven communities.

---

## ✨ Features

- 💎 Deposit and withdraw diamonds from your personal bank  
- 🏦 Securely track your diamond balance with `/balance`  
- 💸 Send diamonds directly to other players with `/pay` — even when they're offline  
- 🛒 Buy and sell items with a fully-featured Shop, newest listings first  
- 🔍 Search the Shop for the item you're holding  
- 🔄 Refresh the Shop without closing the menu  
- ↩️ Take back your own listings at any time  
- 📦 Reclaim expired shop listings with ease  
- 🏆 See the richest players with `/leaderboard`  
- 🛡️ Admin tools to set and review player balances  
- 📁 Per-player data stored in easy-to-read JSON files  
- 🌍 100% server-side — no client mod required!  

---

## 🔧 Commands

- `/deposit <qty>` – Deposit diamonds from your inventory into your bank  
- `/withdraw <qty>` – Withdraw diamonds from your bank into your inventory  
- `/balance` – Check your current diamond balance  
- `/pay <player> <qty>` – Transfer diamonds to another player (online or offline)  
- `/shop` – Open the Shop GUI to view and buy listings (click your own listing to take it back)  
- `/shop sell <price>` – Sell the item in your hand for the specified price  
- `/shop list` – Search the Shop for the item you're holding  
- `/shop cancel` – View your active listings and take them back  
- `/shop expired` – View and reclaim your expired listings  
- `/leaderboard` – Show the top 10 richest players  

### 🛡️ Admin Commands (OP required)

- `/shopadmin set <player> <amount>` – Set a player's diamond balance (works for offline players too)  
- `/shopadmin list` – List all players and their balances  

> Upgrading from an older version? The Auction House is now called the **Shop**, so `/ah` is now `/shop`. Your existing listings carry over automatically.

---

## ⚙️ Configuration

On first start, EasyEconomy creates `config/EasyEconomy.json`:

```json
{
  "maxListingsPerPlayer": 20,
  "listingDurationHours": 24
}
```

- `maxListingsPerPlayer` – How many listings one player can have at once, including expired ones that haven't been reclaimed yet (`0` = no limit)  
- `listingDurationHours` – How long a listing stays in the Shop before it expires  

Changes take effect after a server restart. Invalid values fall back to the defaults, with a warning in the server log.

---

## 🧩 Requirements

- [Fabric Loader](https://fabricmc.net/use/)  
- [Fabric API](https://modrinth.com/mod/fabric-api)  

---

## 🌟 Why EasyEconomy?

Because diamonds > emeralds.  
EasyEconomy keeps your server’s economy simple, shiny, and fun — without confusing currency systems or complicated setups.  

Bank your diamonds, trade them with friends, and make your fortune in the Shop.  

---

## 📜 License

This mod is licensed under the GNU AGPLv3 Licence.  

---

## 💬 Feedback

Found a bug or have a feature suggestion?  
Open an issue or PR on GitHub!  
