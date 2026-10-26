package me.sat7.dynamicshop.utilities;

import java.util.UUID;

import me.sat7.dynamicshop.user.UserService;
import org.bukkit.entity.Player;

/**
 * Legacy facade over {@link UserService}. Every method delegates to the
 * service; new code should call it directly. Kept so callers can be
 * migrated slice by slice.
 */
public final class UserUtil
{
    private UserUtil()
    {

    }

    public static void Init()
    {
        UserService.getInstance().Init();
    }

    public static void CreateNewPlayerData(Player player)
    {
        UserService.getInstance().CreateNewPlayerData(player);
    }

    public static void CreateDummyPlayerData(Player sender, int count)
    {
        UserService.getInstance().CreateDummyPlayerData(sender, count);
    }

    public static boolean RecreateUserData(Player player)
    {
        return UserService.getInstance().RecreateUserData(player);
    }

    public static void OnPluginDisable()
    {
        UserService.getInstance().OnPluginDisable();
    }

    public static void LoadTradeLimitDataFromYML()
    {
        UserService.getInstance().LoadTradeLimitDataFromYML();
    }
    public static void SaveTradeLimitDataToYML()
    {
        UserService.getInstance().SaveTradeLimitDataToYML();
    }
    public static void RepetitiveTask()
    {
        UserService.getInstance().RepetitiveTask();
    }

    public static int GetPlayerTradingVolume(Player player, String shopName, String hash)
    {
        return UserService.getInstance().GetPlayerTradingVolume(player, shopName, hash);
    }

    public static void OnPlayerTradeLimitedItem(Player player, String shopName, String hash, int amount, boolean isSell)
    {
        UserService.getInstance().OnPlayerTradeLimitedItem(player, shopName, hash, amount, isSell);
    }

    public static int CheckTradeLimitPerPlayer(Player player, String shopName, int tradeIdx, String hash, int tradeAmount, boolean isSell)
    {
        return UserService.getInstance().CheckTradeLimitPerPlayer(player, shopName, tradeIdx, hash, tradeAmount, isSell);
    }

    public static int GetTradingLimitLeft(Player player, String shopName, int itemIdx, String hash, boolean isSell)
    {
        return UserService.getInstance().GetTradingLimitLeft(player, shopName, itemIdx, hash, isSell);
    }

    public static void ClearTradeLimitData(String shopName)
    {
        UserService.getInstance().ClearTradeLimitData(shopName);
    }
    public static void ClearTradeLimitData(UUID player, String shopName)
    {
        UserService.getInstance().ClearTradeLimitData(player, shopName);
    }
    public static void ClearTradeLimitData(String shopName, int idx)
    {
        UserService.getInstance().ClearTradeLimitData(shopName, idx);
    }
    public static void ClearTradeLimitData(String shopName, String hash)
    {
        UserService.getInstance().ClearTradeLimitData(shopName, hash);
    }

    public static void OnRenameShop(String shopName, String newName)
    {
        UserService.getInstance().OnRenameShop(shopName, newName);
    }
    public static void OnMergeShop(String shopA, String shopB)
    {
        UserService.getInstance().OnMergeShop(shopA, shopB);
    }
}
