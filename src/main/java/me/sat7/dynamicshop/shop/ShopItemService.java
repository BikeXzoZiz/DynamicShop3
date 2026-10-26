package me.sat7.dynamicshop.shop;

import java.text.SimpleDateFormat;
import java.util.*;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.models.DSItem;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import me.sat7.dynamicshop.utilities.HashUtil;
import me.sat7.dynamicshop.utilities.MathUtil;
import me.sat7.dynamicshop.utilities.RotationUtil;
import me.sat7.dynamicshop.utilities.UserUtil;
import me.sat7.dynamicshop.utilities.WorthUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import static me.sat7.dynamicshop.utilities.LangUtil.t;

/**
 * CRUD of items and pages inside a shop config: finding slots/items, adding,
 * editing and removing trades, page insertion/deletion/swapping, sorting, and
 * per-item trade limits.
 *
 * <p>State lives in {@link ShopRegistry}; this class only operates on it.
 * {@code getInstance()} is a migration scaffold toward constructor injection.</p>
 */
public final class ShopItemService
{
    private static final ShopItemService INSTANCE = new ShopItemService(ShopRegistry.getInstance());

    public static ShopItemService getInstance()
    {
        return INSTANCE;
    }

    private final ShopRegistry registry;

    public ShopItemService(ShopRegistry registry)
    {
        this.registry = registry;
    }

    // 상점에서 빈 슬롯 찾기
    public int findEmptyShopSlot(String shopName, int startIdx, boolean addPage)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if(data == null)
            return -1;

        if(startIdx < 0)
            startIdx = 0;

        int idx = startIdx;
        while(data.get().contains(String.valueOf(idx)))
            idx++;

        if (data.get().getInt("Options.page") < idx / 45 + 1)
        {
            if(addPage)
            {
                data.get().set("Options.page", idx / 45 + 1);
                data.save();
                return idx;
            }

            return -1;
        }

        return idx;
    }

    // 상점에서 아이탬타입 찾기
    public int findItemFromShop(String shopName, ItemStack item)
    {
        if (item == null || item.getType().isAir())
            return -1;

        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return -1;

        int idx = 0;
        for (String s : data.get().getKeys(false))
        {
            if (idx == 0) //Just skips the first data. (=Options). At least it should be much faster than 'tryCatch+parseInt'.
            {
                idx++;
                continue;
            }

            if (!data.get().contains(s + ".value"))
                continue; // 장식용임

            if (data.get().getString(s + ".mat").equals(item.getType().toString()))
            {
                String metaStr = data.get().getString(s + ".itemStack");

                if (metaStr == null && !item.hasItemMeta())
                {
                    return Integer.parseInt(s);
                }

                if (metaStr != null && metaStr.equals(item.getItemMeta().toString()))
                {
                    return Integer.parseInt(s);
                }
            }
        }
        return -1;
    }

    public boolean hashExist(String shopName, String hash)
    {
        if (hash == null || hash.isEmpty())
            return false;

        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return false;

        int idx = 0;
        for (String s : data.get().getKeys(false))
        {
            if (idx == 0)
            {
                idx++;
                continue;
            }

            if (!data.get().contains(s + ".value"))
                continue; // 장식용임

            String compare = HashUtil.CreateHashString(data.get().getString(s + ".mat"), data.get().getString(s + ".itemStack"));
            if (hash.equals(compare))
            {
                return true;
            }
        }

        return false;
    }

    // 상점에 아이탬 추가
    public boolean addItemToShop(String shopName, int idx, DSItem dsItem)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return false;

        try
        {
            data.get().set(idx + ".mat", dsItem.itemStack.getType().toString());

            if (dsItem.itemStack.hasItemMeta())
            {
                data.get().set(idx + ".itemStack", dsItem.itemStack.getItemMeta());
            }
            else
            {
                data.get().set(idx + ".itemStack", null);
            }

            if (dsItem.buyValue > 0)
            {
                data.get().set(idx + ".value", dsItem.buyValue);
                if (dsItem.buyValue == dsItem.sellValue)
                {
                    data.get().set(idx + ".value2", null);
                }
                else
                {
                    data.get().set(idx + ".value2", dsItem.sellValue);
                }

                if (dsItem.minPrice > 0.0001)
                {
                    data.get().set(idx + ".valueMin", dsItem.minPrice);
                }
                else
                {
                    data.get().set(idx + ".valueMin", null);
                }

                if (dsItem.maxPrice > 0.0001)
                {
                    data.get().set(idx + ".valueMax", dsItem.maxPrice);
                }
                else
                {
                    data.get().set(idx + ".valueMax", null);
                }

                data.get().set(idx + ".median", dsItem.median);
                data.get().set(idx + ".stock", dsItem.stock);

                if (dsItem.maxStock > 0)
                {
                    data.get().set(idx + ".maxStock", dsItem.maxStock);
                }
                else
                {
                    data.get().set(idx + ".maxStock", null);
                }

                if (dsItem.discount > 0)
                {
                    data.get().set(idx + ".discount", dsItem.discount);
                }
                else
                {
                    data.get().set(idx + ".discount", null);
                }

                if (dsItem.sellLimit != 0 || dsItem.buyLimit != 0)
                {
                    data.get().set(idx + ".tradeLimitPerPlayer.sell", dsItem.sellLimit);
                    data.get().set(idx + ".tradeLimitPerPlayer.buy", dsItem.buyLimit);
                    data.get().set(idx + ".tradeLimitPerPlayer.interval", dsItem.tradeLimitInterval);
                    data.get().set(idx + ".tradeLimitPerPlayer.nextTimer", dsItem.tradeLimitNextTimer);
                }
                else
                {
                    data.get().set(idx + ".tradeLimitPerPlayer", null);
                }
            }
            else
            {
                // idx,null하면 안됨. 존재는 하되 하위 데이터만 없어야함.
                data.get().set(idx + ".value", null);
                data.get().set(idx + ".value2", null);
                data.get().set(idx + ".valueMin", null);
                data.get().set(idx + ".valueMax", null);
                data.get().set(idx + ".median", null);
                data.get().set(idx + ".stock", null);
                data.get().set(idx + ".maxStock", null);
                data.get().set(idx + ".discount", null);
                data.get().set(idx + ".tradeLimitPerPlayer", null);
            }

            data.save();

            RotationUtil.UpdateCurrentRotationData(shopName, idx);

            return true;
        }
        catch (Exception e)
        {
            DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " ERR.AddItemToShop.");
            for (StackTraceElement s : e.getStackTrace())
            {
                DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " " + s.toString());
            }
            return false;
        }
    }

    // 상점 아이탬의 value, median, stock을 수정
    public void editShopItem(String shopName, int idx, DSItem dsItem)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return;

        data.get().set(idx + ".value", dsItem.buyValue);
        if (dsItem.buyValue == dsItem.sellValue)
        {
            data.get().set(idx + ".value2", null);
        }
        else
        {
            data.get().set(idx + ".value2", dsItem.sellValue);
        }

        if (dsItem.minPrice > 0.0001)
        {
            data.get().set(idx + ".valueMin", dsItem.minPrice);
        }
        else
        {
            data.get().set(idx + ".valueMin", null);
        }

        if (dsItem.maxPrice > 0.0001)
        {
            data.get().set(idx + ".valueMax", dsItem.maxPrice);
        }
        else
        {
            data.get().set(idx + ".valueMax", null);
        }

        if (dsItem.maxStock > 0)
        {
            data.get().set(idx + ".maxStock", dsItem.maxStock);
        }
        else
        {
            data.get().set(idx + ".maxStock", null);
        }

        data.get().set(idx + ".median", dsItem.median);
        data.get().set(idx + ".stock", dsItem.stock);

        if (dsItem.discount > 0)
            data.get().set(idx + ".discount", dsItem.discount);
        else
            data.get().set(idx + ".discount", null);

        if (dsItem.sellLimit != 0 || dsItem.buyLimit != 0)
        {
            data.get().set(idx + ".tradeLimitPerPlayer.sell", dsItem.sellLimit);
            data.get().set(idx + ".tradeLimitPerPlayer.buy", dsItem.buyLimit);
            data.get().set(idx + ".tradeLimitPerPlayer.interval", dsItem.tradeLimitInterval);
            data.get().set(idx + ".tradeLimitPerPlayer.nextTimer", dsItem.tradeLimitNextTimer);
        }
        else
        {
            data.get().set(idx + ".tradeLimitPerPlayer", null);
            UserUtil.ClearTradeLimitData(shopName, idx);
        }

        data.save();

        RotationUtil.UpdateCurrentRotationData(shopName, idx);
    }

    // 상점에서 아이탬 제거
    public void removeItemFromShop(String shopName, int idx)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return;

        UserUtil.ClearTradeLimitData(shopName, idx); // 상점에서 데이터 지우기 전에 실행.

        data.get().set(String.valueOf(idx), null);
        data.save();

        RotationUtil.UpdateCurrentRotationData(shopName, idx);
    }

    // 상점 페이지 삽입
    public void insetShopPage(String shopName, int page)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return;

        data.get().set("Options.page", data.get().getInt("Options.page") + 1);

        for (int i = data.get().getInt("Options.page") * 45; i >= (page - 1) * 45; i--)
        {
            ConfigurationSection temp = data.get().getConfigurationSection(String.valueOf(i));
            data.get().set(String.valueOf(i + 45), temp);
            data.get().set(String.valueOf(i), null);
        }

        data.save();
        data.reload();
    }

    // 상점 페이지 삭제
    public void deleteShopPage(String shopName, int page)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return;

        data.get().set("Options.page", data.get().getInt("Options.page") - 1);

        for (String s : data.get().getKeys(false))
        {
            try
            {
                int i = Integer.parseInt(s);

                if (i >= (page - 1) * 45 && i < page * 45)
                {
                    data.get().set(s, null);
                } else if (i >= page * 45)
                {
                    ConfigurationSection temp = data.get().getConfigurationSection(s);
                    data.get().set(String.valueOf(i - 45), temp);
                    data.get().set(s, null);
                }

            } catch (Exception ignored)
            {
            }
        }

        data.save();
        data.reload();
    }

    // 상점 페이지 스왑
    public boolean SwapPage(String shopName, int pageA, int pageB)
    {
        if(pageA == pageB)
            return true;

        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return false;

        HashMap<Integer, Object> tempA = new HashMap<>();
        HashMap<Integer, Object> tempB = new HashMap<>();

        for (String s : data.get().getKeys(false))
        {
            try
            {
                int i = Integer.parseInt(s);
                if (i >= (pageA - 1) * 45 && i < pageA * 45)
                {
                    tempA.put(i, data.get().get(s));
                    data.get().set(s, null);
                }
                if (i >= (pageB - 1) * 45 && i < pageB * 45)
                {
                    tempB.put(i, data.get().get(s));
                    data.get().set(s, null);
                }

            } catch (Exception ignored)
            {
            }
        }

        tempA.forEach((key, value) ->
        {
            key += (pageB - pageA) * 45;
            data.get().set(String.valueOf(key), value);
        });
        tempB.forEach((key, value) ->
        {
            key += (pageA - pageB) * 45;
            data.get().set(String.valueOf(key), value);
        });

        tempA.clear();
        tempB.clear();

        data.save();
        data.reload();
        return true;
    }

    public boolean IsPageEmpty(String shopName, int page)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return true;

        if (page < 1 || page > GetShopMaxPage(shopName))
            return true;

        for (String s : data.get().getKeys(false))
        {
            try
            {
                int i = Integer.parseInt(s);
                if (i >= (page - 1) * 45 && i < page * 45)
                    return false;
            } catch (Exception ignore)
            {
            }
        }

        return true;
    }

    public void SetToRecommendedValueAll(String shop, CommandSender sender)
    {
        CustomConfig data = registry.getShopConfig(shop);
        if (data == null)
            return;

        for (String itemIndex : data.get().getKeys(false))
        {
            try
            {
                int i = Integer.parseInt(itemIndex); // options에 대해 적용하지 않기 위해.

                if (!data.get().contains(itemIndex + ".value"))
                    continue; // 장식용은 스킵

                String itemName = data.get().getString(itemIndex + ".mat");

                double worth = WorthUtil.ccWorth.get().getDouble(itemName);
                if (worth == 0)
                {
                    itemName = itemName.replace("-", "");
                    itemName = itemName.replace("_", "");
                    itemName = itemName.toLowerCase();

                    worth = WorthUtil.ccWorth.get().getDouble(itemName);
                }

                if (worth != 0)
                {
                    int discount = data.get().getInt(itemIndex + ".discount");
                    int sellLimit = data.get().getInt(itemIndex + ".tradeLimitPerPlayer.sell");
                    int buyLimit = data.get().getInt(itemIndex + ".tradeLimitPerPlayer.buy");
                    long tradeLimitInterval = data.get().getLong(itemIndex + ".tradeLimitPerPlayer.interval");
                    long tradeLimitNextTimer = data.get().getLong(itemIndex + ".tradeLimitPerPlayer.nextTimer");

                    int sugMid = CalcRecommendedMedian(worth, ConfigUtil.GetNumberOfPlayer());
                    DSItem temp = new DSItem(null, worth, worth, 0.0001f, -1, sugMid, sugMid, -1, discount,
                                             sellLimit, buyLimit, tradeLimitInterval, tradeLimitNextTimer);
                    editShopItem(shop, i, temp);
                } else
                {
                    if (sender != null)
                        sender.sendMessage(DynamicShop.dsPrefix(sender) + t(sender, "ERR.NO_RECOMMEND_DATA") + " : " + itemName);
                }
            } catch (Exception ignored)
            {
            }
        }
    }

    public int CalcRecommendedMedian(double worth, int numberOfPlayer)
    {
        return (int) (4 / (Math.pow(worth, 0.35)) * 1000 * numberOfPlayer);
    }

    public void SortShopDataAll()
    {
        for(String s : registry.configFiles().keySet())
        {
            SortShopData(s);
        }
    }

    // yml 파일 안의 거래 인덱스들을 정렬해서 다시 작성함
    public void SortShopData(String shopName)
    {
        CustomConfig data = registry.getShopConfig(shopName);

        HashMap<Integer, Object> sortData = new HashMap<>();

        for(String s : data.get().getKeys(false))
        {
            try
            {
                int dummy = Integer.parseInt(s); // 아이템 데이터가 아닌걸 건너뛰기 위함

                sortData.put(dummy, data.get().get(s));
                data.get().set(s, null);
            }
            catch(Exception ignore){}
        }

        Object[] keys = sortData.keySet().toArray();
        Arrays.sort(keys);

        for(Object o : keys)
        {
            data.get().set(o.toString(), sortData.get(o));
        }

        data.save();
    }

    public int GetShopItemCount(String shopName, Boolean excludeInfiniteStock, Boolean excludeFixedPrice)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if(data == null)
            return -1;

        int count = 0;
        for (String item : data.get().getKeys(false))
        {
            try
            {
                int i = Integer.parseInt(item); // options에 대해 적용하지 않기 위해.
                if (!data.get().contains(item + ".value")) continue; // 장식용은 스킵

                int stock = data.get().getInt(item + ".stock");
                if (excludeInfiniteStock && stock < 1) continue; // 무한재고에 대해서는 스킵
                int oldMedian = data.get().getInt(item + ".median");
                if (excludeFixedPrice && oldMedian < 1) continue; // 고정가 상품에 대해서는 스킵

                count++;
            }
            catch (Exception ignore){}
        }

        return count;
    }

    public int GetShopMaxPage(String shopName)
    {
        CustomConfig data = registry.getShopConfig(shopName);
        if (data == null)
            return 0;

        return data.get().getConfigurationSection("Options").getInt("page");
    }

    public int GetSellLimitPerPlayer(String shopName, int idx)
    {
        CustomConfig data = registry.getShopConfig(shopName);

        if (!data.get().contains(idx + ".tradeLimitPerPlayer"))
        {
            return 0;
        }

        return data.get().getInt(idx + ".tradeLimitPerPlayer.sell");
    }

    public int GetBuyLimitPerPlayer(String shopName, int idx)
    {
        CustomConfig data = registry.getShopConfig(shopName);

        if (!data.get().contains(idx + ".tradeLimitPerPlayer"))
        {
            return 0;
        }

        return data.get().getInt(idx + ".tradeLimitPerPlayer.buy");
    }

    private final SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yyyy HH:mm:ss");
    public String GetTradeLimitNextResetTime(String shopName, int idx)
    {
        CustomConfig data = registry.getShopConfig(shopName);

        boolean somethingChanged = false;

        long interval = data.get().getLong(idx + ".tradeLimitPerPlayer.interval");
        if (interval == 0)
        {
            interval = MathUtil.dayInMilliSeconds;
            data.get().set(idx + ".tradeLimitPerPlayer.interval", interval);
            somethingChanged = true;
        }

        long next = data.get().getLong(idx + ".tradeLimitPerPlayer.nextTimer");
        if (next == 0)
        {
            next = System.currentTimeMillis();
            next = MathUtil.RoundDown_Time_Hour(next);
            data.get().set(idx + ".tradeLimitPerPlayer.nextTimer", next);

            somethingChanged = true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime > next)
        {
            while (currentTime > next)
            {
                next += interval;
            }

            next = MathUtil.RoundDown_Time_Hour(next);
            data.get().set(idx + ".tradeLimitPerPlayer.nextTimer", next);

            UserUtil.ClearTradeLimitData(shopName, idx);
            somethingChanged = true;
        }

        if (somethingChanged)
            registry.markDirty(shopName);

        return sdf.format(next);
    }
}
