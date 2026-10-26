package me.sat7.dynamicshop.utilities;

import java.util.*;

import lombok.NonNull;
import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.models.DSItem;
import me.sat7.dynamicshop.shop.ShopItemService;
import me.sat7.dynamicshop.shop.ShopRegistry;
import me.sat7.dynamicshop.shop.ShopSearchService;
import me.sat7.dynamicshop.shop.ShopStockService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy facade over the shop services. Every method delegates to
 * {@link ShopRegistry}, {@link ShopItemService}, {@link ShopSearchService}
 * or {@link ShopStockService}; new code should call those directly.
 * Kept so callers can be migrated slice by slice.
 */
public final class ShopUtil
{
    private ShopUtil()
    {

    }

    public static void Reload()
    {
        ReloadAllShop();
        ConvertOldShopData();
        BackwardCompatibility();
        SetupSampleShopFile();
        SortShopDataAll();
    }

    public static void ReloadAllShop()
    {
        ShopRegistry.getInstance().loadAll();
    }

    public static void SetupSampleShopFile()
    {
        ShopRegistry.getInstance().setupSampleShop();
    }

    // Shop.yml 한덩어리로 되있는 데이터를 새 버전 방식으로 변환함 (v2 -> v3)
    public static void ConvertOldShopData()
    {
        ShopRegistry.getInstance().convertOldShopData();
    }

    public static void BackwardCompatibility()
    {
        ShopRegistry.getInstance().applyBackwardCompatibility();
    }

    // 상점에서 빈 슬롯 찾기
    public static int findEmptyShopSlot(String shopName, int startIdx, boolean addPage)
    {
        return ShopItemService.getInstance().findEmptyShopSlot(shopName, startIdx, addPage);
    }

    // 상점에서 아이탬타입 찾기
    public static int findItemFromShop(String shopName, ItemStack item)
    {
        return ShopItemService.getInstance().findItemFromShop(shopName, item);
    }

    public static boolean hashExist(String shopName, String hash)
    {
        return ShopItemService.getInstance().hashExist(shopName, hash);
    }

    // 상점에 아이탬 추가
    public static boolean addItemToShop(String shopName, int idx, DSItem dsItem)
    {
        return ShopItemService.getInstance().addItemToShop(shopName, idx, dsItem);
    }

    // 상점 아이탬의 value, median, stock을 수정
    public static void editShopItem(String shopName, int idx, DSItem dsItem)
    {
        ShopItemService.getInstance().editShopItem(shopName, idx, dsItem);
    }

    // 상점에서 아이탬 제거
    public static void removeItemFromShop(String shopName, int idx)
    {
        ShopItemService.getInstance().removeItemFromShop(shopName, idx);
    }

    // 상점 페이지 삽입
    public static void insetShopPage(String shopName, int page)
    {
        ShopItemService.getInstance().insetShopPage(shopName, page);
    }

    // 상점 페이지 삭제
    public static void deleteShopPage(String shopName, int page)
    {
        ShopItemService.getInstance().deleteShopPage(shopName, page);
    }

    // 상점 페이지 스왑
    public static boolean SwapPage(String shopName, int pageA, int pageB)
    {
        return ShopItemService.getInstance().SwapPage(shopName, pageA, pageB);
    }

    public static boolean IsPageEmpty(String shopName, int page)
    {
        return ShopItemService.getInstance().IsPageEmpty(shopName, page);
    }

    // 상점 이름 바꾸기
    public static void renameShop(String shopName, String newName)
    {
        ShopRegistry.getInstance().renameShop(shopName, newName);
    }

    public static void copyShop(String shopName, String newName)
    {
        ShopRegistry.getInstance().copyShop(shopName, newName);
    }

    // 상점 병합
    public static void mergeShop(String shopA, String shopB)
    {
        ShopRegistry.getInstance().mergeShop(shopA, shopB);
    }

    // 상점의 잔액 확인
    public static double getShopBalance(String shopName)
    {
        return ShopRegistry.getInstance().getShopBalance(shopName);
    }

    // 상점의 잔액 수정
    public static void addShopBalance(String shopName, double amount)
    {
        ShopRegistry.getInstance().addShopBalance(shopName, amount);
    }

    // 2틱 후 인벤토리 닫기
    public static void closeInventoryWithDelay(Player player)
    {
        //todo 왜 이렇게 만들었을까??? 2틱 딜레이가 필요한 이유가 뭐지?
        Bukkit.getScheduler().runTaskLater(DynamicShop.plugin, (Runnable) player::closeInventory, 2);
    }

    public static void SetToRecommendedValueAll(String shop, CommandSender sender)
    {
        ShopItemService.getInstance().SetToRecommendedValueAll(shop, sender);
    }

    public static int CalcRecommendedMedian(double worth, int numberOfPlayer)
    {
        return ShopItemService.getInstance().CalcRecommendedMedian(worth, numberOfPlayer);
    }

    public static String[] FindTheBestShopToSell(Player player, ItemStack itemStack)
    {
        return ShopSearchService.getInstance().FindTheBestShopToSell(player, itemStack);
    }

    public static String[] FindTheBestShopToBuy(Player player, ItemStack itemStack)
    {
        return ShopSearchService.getInstance().FindTheBestShopToBuy(player, itemStack);
    }

    public static int GetShopMaxPage(String shopName)
    {
        return ShopItemService.getInstance().GetShopMaxPage(shopName);
    }

    public static void randomChange(Random generator)
    {
        ShopStockService.getInstance().randomChange(generator);
    }

    public static boolean CheckShopLocation(String shopName, Player player)
    {
        return ShopSearchService.getInstance().CheckShopLocation(shopName, player);
    }

    public static int CalcShipping(String shopName, Player player)
    {
        return ShopSearchService.getInstance().CalcShipping(shopName, player);
    }

    public static void SortShopDataAll()
    {
        ShopItemService.getInstance().SortShopDataAll();
    }

    // yml 파일 안의 거래 인덱스들을 정렬해서 다시 작성함
    public static void SortShopData(String shopName)
    {
        ShopItemService.getInstance().SortShopData(shopName);
    }

    public static void ShopYMLBackup()
    {
        ShopRegistry.getInstance().backupShopYmls();
    }

    public static void ShopYMLBackupCull()
    {
        ShopRegistry.getInstance().cullOldBackups();
    }

    public static int GetShopItemCount(String shopName, Boolean excludeInfiniteStock, Boolean excludeFixedPrice)
    {
        return ShopItemService.getInstance().GetShopItemCount(shopName, excludeInfiniteStock, excludeFixedPrice);
    }

    public static int RandomStockFluctuation(Random generator, int stock, int median, double strength)
    {
        return ShopStockService.getInstance().RandomStockFluctuation(generator, stock, median, strength);
    }

    public static int StockStabilizing(Boolean isLegacyMode, Random generator,int stock, int median, double strength)
    {
        return ShopStockService.getInstance().StockStabilizing(isLegacyMode, generator, stock, median, strength);
    }

    public static boolean CheckShopHour(String shopName, Player player)
    {
        return ShopSearchService.getInstance().CheckShopHour(shopName, player);
    }

    public static void SetShopSellCommand(String shopName, int idx, String command)
    {
        ShopRegistry.getInstance().SetShopSellCommand(shopName, idx, command);
    }

    public static void SetShopBuyCommand(String shopName, int idx, String command)
    {
        ShopRegistry.getInstance().SetShopBuyCommand(shopName, idx, command);
    }

    public static void CleanupCommandIndex(String shopName, String sellBuyString)
    {
        ShopRegistry.getInstance().CleanupCommandIndex(shopName, sellBuyString);
    }

    public static void SaveDirtyShop()
    {
        ShopRegistry.getInstance().saveDirtyShops();
    }

    public static void ForceSaveAllShop()
    {
        ShopRegistry.getInstance().forceSaveAll();
    }

    public static int GetSellLimitPerPlayer(String shopName, int idx)
    {
        return ShopItemService.getInstance().GetSellLimitPerPlayer(shopName, idx);
    }

    public static int GetBuyLimitPerPlayer(String shopName, int idx)
    {
        return ShopItemService.getInstance().GetBuyLimitPerPlayer(shopName, idx);
    }

    public static String GetTradeLimitNextResetTime(String shopName, int idx)
    {
        return ShopItemService.getInstance().GetTradeLimitNextResetTime(shopName, idx);
    }

    public static String GetCurrency(@NonNull CustomConfig config)
    {
        return ShopRegistry.getInstance().GetCurrency(config);
    }
    public static String GetCurrency(@NonNull FileConfiguration fileConfiguration)
    {
        return ShopRegistry.getInstance().GetCurrency(fileConfiguration);
    }
}
