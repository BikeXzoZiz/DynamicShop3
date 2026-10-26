package me.sat7.dynamicshop.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.shop.ShopItemService;
import me.sat7.dynamicshop.shop.ShopRegistry;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import me.sat7.dynamicshop.utilities.HashUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Owns all per-user state: the User.yml storage config, the per-player
 * session maps (temp data used by chat-input flows, and the last interacted
 * shop item), and the in-memory trading volume used to enforce per-player
 * trade limits (persisted to User.yml periodically and on shutdown).
 *
 * <p>{@code getInstance()} is a migration scaffold toward constructor
 * injection; the composition root will construct and pass this class
 * explicitly once the static singletons are gone.</p>
 */
public final class UserService
{
    private static final UserService INSTANCE = new UserService();

    public static UserService getInstance()
    {
        return INSTANCE;
    }

    private final CustomConfig ccUser = new CustomConfig(); // 가급적 save 호출 피할 것. onDisable 에서 처리함.
    private final HashMap<UUID, String> userTempData = new HashMap<>();
    private final HashMap<UUID, String> userInteractItem = new HashMap<>();
    private final HashMap<String, HashMap<String, HashMap<UUID, Integer>>> tradingVolume = new HashMap<>();

    private UserService()
    {

    }

    // ---------- accessors (migration scaffold for debug/diagnostic code) ----------

    public CustomConfig userConfig()
    {
        return ccUser;
    }

    public HashMap<UUID, String> tempData()
    {
        return userTempData;
    }

    public HashMap<UUID, String> interactItem()
    {
        return userInteractItem;
    }

    public HashMap<String, HashMap<String, HashMap<UUID, Integer>>> tradeVolume()
    {
        return tradingVolume;
    }

    // ---------- session state ----------

    public void setTempData(UUID uuid, String value)
    {
        userTempData.put(uuid, value);
    }

    public String getTempData(UUID uuid)
    {
        return userTempData.get(uuid);
    }

    public boolean hasTempData(UUID uuid)
    {
        return userTempData.containsKey(uuid);
    }

    public void setInteractItem(UUID uuid, String value)
    {
        userInteractItem.put(uuid, value);
    }

    public String getInteractItem(UUID uuid)
    {
        return userInteractItem.get(uuid);
    }

    // 플레이어가 나갈때 세션 데이터를 정리함.
    public void clearSession(UUID uuid)
    {
        userTempData.remove(uuid);
        userInteractItem.remove(uuid);
    }

    // ---------- lifecycle ----------

    public void Init()
    {
        ccUser.setup("User", null);
        ccUser.get().options().copyDefaults(true);
        BackwardCompatibility();
        ccUser.save();

        LoadTradeLimitDataFromYML();
    }

    private void BackwardCompatibility()
    {
        int userVersion = ConfigUtil.GetConfigVersion();
        if (userVersion < 3)
        {
            for (String s : ccUser.get().getKeys(false))
            {
                ConfigurationSection cs = ccUser.get().getConfigurationSection(s);
                if (cs != null)
                {
                    // 구버전에 있던 데이터를 삭제함.
                    cs.set("tmpString", null);
                    cs.set("interactItem", null);
                }
            }
        }
    }

    public void CreateNewPlayerData(Player player)
    {
        UUID uuid = player.getUniqueId();
        userTempData.put(uuid, "");
        userInteractItem.put(uuid, "");
        ccUser.get().set(uuid + ".lastJoin", System.currentTimeMillis());
        ccUser.get().set(uuid + ".cmdHelp", true);
    }

    public void CreateDummyPlayerData(Player sender, int count)
    {
        if (!DynamicShop.DEBUG_MODE)
            return;

        sender.sendMessage(DynamicShop.dsPrefix(sender) + "Start creating dummy data...");

        Random generator = new Random();
        Object tradingVolumeData = ccUser.get().get(sender.getUniqueId() + ".tradingVolume");

        for (int i = 0; i < count; i++)
        {
            UUID uuid = UUID.randomUUID();
            userTempData.put(uuid, "");
            userInteractItem.put(uuid, "");

            int old = (generator.nextInt() % 100) + 3;
            ccUser.get().set(uuid + ".lastJoin", System.currentTimeMillis() - ((long) old * 1000 * 60 * 60 * 24));

            ccUser.get().set(uuid + ".cmdHelp", true);

            if (i % 5 == 0)
            {
                ccUser.get().set(uuid + ".tradingVolume", tradingVolumeData);
            }
        }

        ccUser.save();

        sender.sendMessage(DynamicShop.dsPrefix(sender) + "Dummy Player Data Created x " + count);
    }

    public boolean RecreateUserData(Player player)
    {
        if (ccUser.get().contains(player.getUniqueId().toString()))
            return true;

        CreateNewPlayerData(player);
        ccUser.save();

        return ccUser.get().contains(player.getUniqueId().toString());
    }

    public void OnPluginDisable()
    {
        SaveTradeLimitDataToYML();
        ccUser.save();
    }

    // ----------------------------------------------------------------------

    // 구매 & 판매 수량 제한
    // - 상점의 데이터는 [상점+idx] 로 관리.
    // - 유저의 데이터는 [상점+hash] 로 관리.
    public void LoadTradeLimitDataFromYML()
    {
        for (String uuid : ccUser.get().getKeys(false))
        {
            ConfigurationSection userDataSection = ccUser.get().getConfigurationSection(uuid + ".tradingVolume");
            if (userDataSection == null)
                continue;

            for (String shopName : userDataSection.getKeys(false))
            {
                ConfigurationSection shopDataSection = userDataSection.getConfigurationSection(shopName);
                if (shopDataSection == null)
                    continue;

                for (String itemHash : shopDataSection.getKeys(false))
                {
                    if (!tradingVolume.containsKey(shopName))
                        tradingVolume.put(shopName, new HashMap<>());

                    if (!tradingVolume.get(shopName).containsKey(itemHash))
                        tradingVolume.get(shopName).put(itemHash, new HashMap<>());

                    tradingVolume.get(shopName).get(itemHash).put(UUID.fromString(uuid), shopDataSection.getInt(itemHash));
                }
            }
        }
    }
    public void SaveTradeLimitDataToYML()
    {
        for (String uuid : ccUser.get().getKeys(false))
        {
            ConfigurationSection userDataSection = ccUser.get().getConfigurationSection(uuid + ".tradingVolume");
            if (userDataSection == null)
                continue;

            ccUser.get().set(uuid + ".tradingVolume", null);
        }

        for (Map.Entry<String, HashMap<String, HashMap<UUID, Integer>>> shopMap : tradingVolume.entrySet())
        {
            CustomConfig data = ShopRegistry.getInstance().getShopConfig(shopMap.getKey());
            if (data == null)
                continue;

            for (Map.Entry<String, HashMap<UUID, Integer>> itemMap : tradingVolume.get(shopMap.getKey()).entrySet())
            {
                String hash = itemMap.getKey();
                if (!ShopItemService.getInstance().hashExist(shopMap.getKey(), hash))
                    continue;

                for (Map.Entry<UUID, Integer> uuidMap : tradingVolume.get(shopMap.getKey()).get(itemMap.getKey()).entrySet())
                {
                    ccUser.get().set(uuidMap.getKey() + ".tradingVolume." + shopMap.getKey() + "." + itemMap.getKey(), uuidMap.getValue());
                }
            }
        }
    }
    public void RepetitiveTask()
    {
        SaveTradeLimitDataToYML();
        ccUser.save();
    }

    public int GetPlayerTradingVolume(Player player, String shopName, String hash)
    {
        UUID uuid = player.getUniqueId();
        if (!tradingVolume.containsKey(shopName) || tradingVolume.get(shopName) == null ||
            !tradingVolume.get(shopName).containsKey(hash) || tradingVolume.get(shopName).get(hash) == null ||
            !tradingVolume.get(shopName).get(hash).containsKey(uuid))
        {
            return 0;
        }

        return tradingVolume.get(shopName).get(hash).get(uuid);
    }

    public void OnPlayerTradeLimitedItem(Player player, String shopName, String hash, int amount, boolean isSell)
    {
        UUID uuid = player.getUniqueId();
        if (!tradingVolume.containsKey(shopName))
            tradingVolume.put(shopName, new HashMap<>());

        if (!tradingVolume.get(shopName).containsKey(hash))
            tradingVolume.get(shopName).put(hash, new HashMap<>());

        if (isSell)
            amount *= -1;

        if (!tradingVolume.get(shopName).get(hash).containsKey(uuid))
        {
            tradingVolume.get(shopName).get(hash).put(uuid, amount);
        }
        else
        {
            tradingVolume.get(shopName).get(hash).put(uuid, tradingVolume.get(shopName).get(hash).get(uuid) + amount);
        }
    }

    public int CheckTradeLimitPerPlayer(Player player, String shopName, int tradeIdx, String hash, int tradeAmount, boolean isSell)
    {
        int limit = isSell ? ShopItemService.getInstance().GetSellLimitPerPlayer(shopName, tradeIdx)
                : ShopItemService.getInstance().GetBuyLimitPerPlayer(shopName, tradeIdx);
        if (limit == 0)
            return tradeAmount;

        int left = GetTradingLimitLeft(player, shopName, tradeIdx, hash, isSell);
        return Math.min(left, tradeAmount);
    }

    public int GetTradingLimitLeft(Player player, String shopName, int itemIdx, String hash, boolean isSell)
    {
        int playerCurrent = GetPlayerTradingVolume(player, shopName, hash);
        int limit = isSell ? ShopItemService.getInstance().GetSellLimitPerPlayer(shopName, itemIdx)
                : ShopItemService.getInstance().GetBuyLimitPerPlayer(shopName, itemIdx);
        if (limit == 0)
            return Integer.MAX_VALUE;

        if (isSell)
        {
            int temp = limit + playerCurrent;
            return Math.max(0, temp);
        }
        else
        {
            int temp = limit - playerCurrent;
            return Math.max(0, temp);
        }
    }

    public void ClearTradeLimitData(String shopName)
    {
        if (!tradingVolume.containsKey(shopName))
            return;

        tradingVolume.remove(shopName);
    }
    public void ClearTradeLimitData(UUID player, String shopName)
    {
        for (Map.Entry<String, HashMap<String, HashMap<UUID, Integer>>> shopMap : tradingVolume.entrySet())
        {
            if (!shopMap.getKey().equals(shopName))
                continue;

            for (Map.Entry<String, HashMap<UUID, Integer>> itemMap : tradingVolume.get(shopMap.getKey()).entrySet())
            {
                for (Map.Entry<UUID, Integer> uuidMap : tradingVolume.get(shopMap.getKey()).get(itemMap.getKey()).entrySet())
                {
                    if (uuidMap.getKey().equals(player))
                    {
                        tradingVolume.get(shopMap.getKey()).get(itemMap.getKey()).remove(player);
                    }
                }
            }
        }
    }
    public void ClearTradeLimitData(String shopName, int idx)
    {
        CustomConfig data = ShopRegistry.getInstance().getShopConfig(shopName);
        if (data == null)
            return;

        String hash = HashUtil.CreateHashString(data.get().getString(idx + ".mat"), data.get().getString(idx + ".itemStack"));

        ClearTradeLimitData(shopName, hash);
    }
    public void ClearTradeLimitData(String shopName, String hash)
    {
        if (!tradingVolume.containsKey(shopName) || tradingVolume.get(shopName) == null ||
            !tradingVolume.get(shopName).containsKey(hash))
            return;

        tradingVolume.get(shopName).remove(hash);
    }

    public void OnRenameShop(String shopName, String newName)
    {
        if (!tradingVolume.containsKey(shopName) || tradingVolume.get(shopName) == null)
            return;

        tradingVolume.put(newName, tradingVolume.get(shopName));
        tradingVolume.remove(shopName);
    }
    public void OnMergeShop(String shopA, String shopB)
    {
        if (!tradingVolume.containsKey(shopB) || tradingVolume.get(shopB) == null)
            return;

        if (!tradingVolume.containsKey(shopA))
            tradingVolume.put(shopA, new HashMap<>());

        HashMap<String, HashMap<UUID, Integer>> clone = (HashMap<String, HashMap<UUID, Integer>>) tradingVolume.get(shopB).clone();
        for (Map.Entry<String, HashMap<UUID, Integer>> shopBData : clone.entrySet())
        {
            tradingVolume.get(shopA).put(shopBData.getKey(), shopBData.getValue());
        }

        tradingVolume.remove(shopB);
    }
}
