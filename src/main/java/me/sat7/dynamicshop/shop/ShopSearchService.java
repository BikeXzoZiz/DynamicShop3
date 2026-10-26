package me.sat7.dynamicshop.shop;

import java.util.ArrayList;
import java.util.Map;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.transactions.Calc;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import me.sat7.dynamicshop.utilities.HashUtil;
import me.sat7.dynamicshop.utilities.ItemsUtil;
import me.sat7.dynamicshop.utilities.UserUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static me.sat7.dynamicshop.utilities.LangUtil.sendMessageWithLocalizedItemName;
import static me.sat7.dynamicshop.utilities.LangUtil.t;
import static me.sat7.dynamicshop.utilities.MathUtil.Clamp;

/**
 * Finds the best shop to buy from or sell to across all registered shops,
 * honoring permissions, enable flags, sign/local shop flags, opening hours,
 * delivery charges, stock and per-player trade limits.
 *
 * <p>State lives in {@link ShopRegistry}; item lookups go through
 * {@link ShopItemService}. {@code getInstance()} is a migration scaffold
 * toward constructor injection.</p>
 */
public final class ShopSearchService
{
    private static final ShopSearchService INSTANCE =
            new ShopSearchService(ShopRegistry.getInstance(), ShopItemService.getInstance());

    public static ShopSearchService getInstance()
    {
        return INSTANCE;
    }

    private final ShopRegistry registry;
    private final ShopItemService itemService;

    public ShopSearchService(ShopRegistry registry, ShopItemService itemService)
    {
        this.registry = registry;
        this.itemService = itemService;
    }

    public String[] FindTheBestShopToSell(Player player, ItemStack itemStack)
    {
        String topShopName = "";
        double bestPrice = -1;
        int tradeIdx = -1;

        String currency = "";
        ArrayList<String> failReason = new ArrayList<>();

        // 접근가능한 상점중 최고가 찾기
        for(Map.Entry<String, CustomConfig> entry : registry.entries())
        {
            CustomConfig data = entry.getValue();

            // 권한 없는 상점
            if(player != null)
            {
                String permission = data.get().getString("Options.permission");
                if (permission != null && permission.length() > 0 && !player.hasPermission(permission) && !player.hasPermission(permission + ".sell"))
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-no permission");
                    continue;
                }
            }

            // 비활성화된 상점
            boolean enable = data.get().getBoolean("Options.enable", true);
            if (!enable)
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-shop is disabled");
                continue;
            }

            // 표지판 전용 상점, 지역상점, 잡포인트 상점
            boolean outside = !CheckShopLocation(entry.getKey(), player);
            if (outside && data.get().contains("Options.flag.localshop") && !data.get().contains("Options.flag.deliverycharge")) {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-local shop");
                continue;
            }

            if (data.get().contains("Options.flag.signshop"))
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-sign shop");
                continue;
            }

            // 영업시간 확인
            if (player != null && !CheckShopHour(entry.getKey(), player))
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-not open hours");
                continue;
            }

            double deliveryCosts = CalcShipping(entry.getKey(), player);
            if (deliveryCosts == -1)
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-Infinite Delivery Fee (Other World)");
                continue;
            }

            int sameItemIdx = itemService.findItemFromShop(entry.getKey(), itemStack);
            if (sameItemIdx != -1)
            {
                String tradeType = data.get().getString(sameItemIdx + ".tradeType");
                if (tradeType != null && tradeType.equalsIgnoreCase("BuyOnly"))
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-buy only");
                    continue; // 구매만 가능함
                }

                // 여러 재화로 취급중인 경우 지원 안함.
                 if (currency.isEmpty())
                {
                    currency = registry.GetCurrency(data);
                }
                else if (!currency.equalsIgnoreCase(registry.GetCurrency(data)))
                {
                    if(player != null)
                    {
                        boolean useLocalizedName = ConfigUtil.GetLocalizedItemName();
                        if (useLocalizedName)
                        {
                            String message = DynamicShop.dsPrefix(player) + t(player, "MESSAGE.Q_SEARCH_FAIL_CURRENCY") + " - <item>";
                            sendMessageWithLocalizedItemName(player, message, itemStack.getType());
                        }
                        else
                        {
                            player.sendMessage(DynamicShop.dsPrefix(player) + t(player, "MESSAGE.Q_SEARCH_FAIL_CURRENCY") + " - " + ItemsUtil.getBeautifiedName(itemStack.getType()));
                        }
                    }

                    return new String[]{"","-2"};
                }

                // 상점에 돈이 없음
                if (registry.getShopBalance(entry.getKey()) != -1 &&
                        registry.getShopBalance(entry.getKey()) < Calc.calcTotalCost(entry.getKey(), String.valueOf(sameItemIdx), itemStack.getAmount())[0])
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-no money in shop");
                    continue;
                }

                // 최대 재고를 넘겨서 매입 거절
                int maxStock = data.get().getInt(sameItemIdx + ".maxStock", -1);
                int stock = data.get().getInt(sameItemIdx + ".stock");
                if (maxStock != -1 && maxStock <= stock)
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-shop reaches max stock");
                    continue;
                }

                // 플레이어 당 거래량 제한 확인
                int sellLimit = itemService.GetSellLimitPerPlayer(entry.getKey(), sameItemIdx);
                if (player != null && sellLimit != 0)
                {
                    int tradeAmount = UserUtil.CheckTradeLimitPerPlayer(player, entry.getKey(), sameItemIdx, HashUtil.GetItemHash(itemStack), itemStack.getAmount(), true);
                    if (tradeAmount == 0)
                    {
                        if (DynamicShop.DEBUG_LOG_ENABLED)
                            failReason.add("shopName:" + entry.getKey() + "-Reached trading volume limit");
                        continue;
                    }
                }

                double value = Calc.getCurrentPrice(entry.getKey(), String.valueOf(sameItemIdx), false);

                value -= deliveryCosts;

                if (topShopName.isEmpty() || bestPrice < value)
                {
                    topShopName = entry.getKey();
                    bestPrice = value;
                    tradeIdx = sameItemIdx;
                }
            }
        }

        if (DynamicShop.DEBUG_LOG_ENABLED && topShopName.equals("") && failReason.size() > 0)
        {
            DynamicShop.PrintConsoleDbgLog("No shops available for sell. player:" + player + " itemType:" + itemStack.getType() + " Details:");
            for (String s : failReason)
            {
                DynamicShop.PrintConsoleDbgLog(" - " + s);
            }
        }

        return new String[]{topShopName, Integer.toString(tradeIdx)};
    }

    public String[] FindTheBestShopToBuy(Player player, ItemStack itemStack)
    {
        String topShopName = "";
        double bestPrice = Double.MAX_VALUE;
        int tradeIdx = -1;

        int currencyInt = -1;

        ArrayList<String> failReason = new ArrayList<>();

        // 접근가능한 상점중 최저가 찾기
        for(Map.Entry<String, CustomConfig> entry : registry.entries())
        {
            CustomConfig data = entry.getValue();

            // 권한 없는 상점
            String permission = data.get().getString("Options.permission");
            if (permission != null && permission.length() > 0 && !player.hasPermission(permission) && !player.hasPermission(permission + ".buy"))
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-no permission");
                continue;
            }

            // 비활성화된 상점
            boolean enable = data.get().getBoolean("Options.enable", true);
            if (!enable)
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-shop is disabled");
                continue;
            }

            // 표지판 전용 상점, 지역상점, 잡포인트 상점
            boolean outside = !CheckShopLocation(entry.getKey(), player);
            if (outside && data.get().contains("Options.flag.localshop") && !data.get().contains("Options.flag.deliverycharge")) {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-local shop");
                continue;
            }

            if (data.get().contains("Options.flag.signshop"))
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-sign shop");
                continue;
            }

            // 영업시간 확인
            if (!CheckShopHour(entry.getKey(), player))
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-not open hours");
                continue;
            }

            double deliveryCosts = CalcShipping(entry.getKey(), player);
            if (deliveryCosts == -1)
            {
                if (DynamicShop.DEBUG_LOG_ENABLED)
                    failReason.add("shopName:" + entry.getKey() + "-Infinite Delivery Fee (Other World)");
                continue;
            }

            int sameItemIdx = itemService.findItemFromShop(entry.getKey(), itemStack);

            if (sameItemIdx != -1)
            {
                String tradeType = data.get().getString(sameItemIdx + ".tradeType");

                if (tradeType != null && tradeType.equalsIgnoreCase("SellOnly"))
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-sell only");
                    continue;
                }

                // 재고가 없음
                int stock = data.get().getInt(sameItemIdx + ".stock");
                if (stock != -1 && stock < 2)
                {
                    if (DynamicShop.DEBUG_LOG_ENABLED)
                        failReason.add("shopName:" + entry.getKey() + "-Out of stock");
                    continue;
                }

                // 여러 재화로 취급중인 경우 지원 안함.
                int tempCurrencyIndex = 0;
                if (registry.GetCurrency(data).equalsIgnoreCase(Constants.S_JOBPOINT))
                {
                    tempCurrencyIndex = 1;
                }
                else if (registry.GetCurrency(data).equalsIgnoreCase(Constants.S_PLAYERPOINT))
                {
                    tempCurrencyIndex = 2;
                }
                else if (registry.GetCurrency(data).equalsIgnoreCase(Constants.S_EXP))
                {
                    tempCurrencyIndex = 3;
                }

                if (currencyInt == -1)
                {
                    currencyInt = tempCurrencyIndex;
                }
                else if (currencyInt != tempCurrencyIndex)
                {
                    if(player != null)
                    {
                        boolean useLocalizedName = ConfigUtil.GetLocalizedItemName();
                        if (useLocalizedName)
                        {
                            String message = DynamicShop.dsPrefix(player) + t(player, "MESSAGE.Q_SEARCH_FAIL_CURRENCY") + " - <item>";
                            sendMessageWithLocalizedItemName(player, message, itemStack.getType());
                        }
                        else
                        {
                            player.sendMessage(DynamicShop.dsPrefix(player) + t(player, "MESSAGE.Q_SEARCH_FAIL_CURRENCY") + " - " + ItemsUtil.getBeautifiedName(itemStack.getType()));
                        }
                    }

                    return new String[]{"","-2"};
                }

                // 플레이어 당 거래량 제한 확인
                int buyLimit = itemService.GetBuyLimitPerPlayer(entry.getKey(), sameItemIdx);
                if (player != null && buyLimit != 0)
                {
                    int tradeAmount = UserUtil.CheckTradeLimitPerPlayer(player, entry.getKey(), sameItemIdx, HashUtil.GetItemHash(itemStack), itemStack.getAmount(), false);
                    if (tradeAmount == 0)
                    {
                        if (DynamicShop.DEBUG_LOG_ENABLED)
                            failReason.add("shopName:" + entry.getKey() + "-Trading volume limit reached");
                        continue;
                    }
                }

                double value = Calc.getCurrentPrice(entry.getKey(), String.valueOf(sameItemIdx), true);

                value += deliveryCosts;

                if (bestPrice > value)
                {
                    topShopName = entry.getKey();
                    bestPrice = value;
                    tradeIdx = sameItemIdx;
                }
            }
        }

        if (DynamicShop.DEBUG_LOG_ENABLED && topShopName.equals("") && failReason.size() > 0)
        {
            DynamicShop.PrintConsoleDbgLog("No shops available for purchase. player:" + player + " itemType:" + itemStack.getType() + " Details:");
            for (String s : failReason)
            {
                DynamicShop.PrintConsoleDbgLog(" - " + s);
            }
        }

        return new String[]{topShopName, Integer.toString(tradeIdx)};
    }

    public boolean CheckShopLocation(String shopName, Player player)
    {
        CustomConfig shopData = registry.getShopConfig(shopName);
        if (shopData == null)
            return true;

        ConfigurationSection shopConf = shopData.get().getConfigurationSection("Options");
        if (shopConf == null)
            return true;

        if (!shopConf.contains("flag.localshop") || !shopConf.contains("world") || !shopConf.contains("pos1") || !shopConf.contains("pos2")) {
            return true;
        }

        boolean inside = player.getWorld().getName().equals(shopConf.getString("world"));

        String[] shopPos1 = shopConf.getString("pos1").split("_");
        String[] shopPos2 = shopConf.getString("pos2").split("_");
        int x1 = Integer.parseInt(shopPos1[0]);
        int y1 = Integer.parseInt(shopPos1[1]);
        int z1 = Integer.parseInt(shopPos1[2]);
        int x2 = Integer.parseInt(shopPos2[0]);
        int y2 = Integer.parseInt(shopPos2[1]);
        int z2 = Integer.parseInt(shopPos2[2]);

        if (!((x1 <= player.getLocation().getBlockX() && player.getLocation().getBlockX() <= x2) ||
                (x2 <= player.getLocation().getBlockX() && player.getLocation().getBlockX() <= x1)))
            inside = false;
        if (!((y1 <= player.getLocation().getBlockY() && player.getLocation().getBlockY() <= y2) ||
                (y2 <= player.getLocation().getBlockY() && player.getLocation().getBlockY() <= y1)))
            inside = false;
        if (!((z1 <= player.getLocation().getBlockZ() && player.getLocation().getBlockZ() <= z2) ||
                (z2 <= player.getLocation().getBlockZ() && player.getLocation().getBlockZ() <= z1)))
            inside = false;

        return inside;
    }

    public int CalcShipping(String shopName, Player player)
    {
        if (player == null)
            return 0;

        int deliverycharge = 0;

        CustomConfig shopData = registry.getShopConfig(shopName);
        if (shopData == null)
            return 0;

        ConfigurationSection shopConf = shopData.get().getConfigurationSection("Options");
        if (shopConf == null)
            return 0;


        if (shopConf.contains("world") && shopConf.contains("pos1") && shopConf.contains("flag.deliverycharge"))
        {
            boolean sameworld = true;
            boolean outside = !CheckShopLocation(shopName, player);

            if (!player.getWorld().getName().equals(shopConf.getString("world"))) sameworld = false;

            String[] shopPos1 = shopConf.getString("pos1").split("_");
            int x1 = Integer.parseInt(shopPos1[0]);
            int y1 = Integer.parseInt(shopPos1[1]);
            int z1 = Integer.parseInt(shopPos1[2]);

            if (!sameworld)
            {
                deliverycharge = -1;
            } else if (outside)
            {
                Location lo = new Location(player.getWorld(), x1, y1, z1);
                int dist = (int) (player.getLocation().distance(lo) * 0.1 * ConfigUtil.GetDeliveryChargeScale());
                deliverycharge = Clamp(dist, ConfigUtil.GetDeliveryChargeMin(), ConfigUtil.GetDeliveryChargeMax());
            }
        }

        return deliverycharge;
    }

    public boolean CheckShopHour(String shopName, Player player)
    {
        CustomConfig shopData = registry.getShopConfig(shopName);
        if (shopData == null)
            return true;

        ConfigurationSection shopConf = shopData.get().getConfigurationSection("Options");
        if (shopConf == null)
            return true;

        if (shopConf.contains("shophours"))
        {
            int curTimeHour = (int) (player.getWorld().getTime()) / 1000 + 6;
            if (curTimeHour > 24) curTimeHour -= 24;

            String[] temp = shopConf.getString("shophours").split("~");

            int open = Integer.parseInt(temp[0]);
            int close = Integer.parseInt(temp[1]);

            if (close > open)
            {
                return open <= curTimeHour && curTimeHour < close;
            } else
            {
                return open <= curTimeHour || curTimeHour < close;
            }
        }
        else
        {
            return true;
        }
    }
}
