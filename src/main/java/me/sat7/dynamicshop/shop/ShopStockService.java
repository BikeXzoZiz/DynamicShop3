package me.sat7.dynamicshop.shop;

import java.util.Map;
import java.util.Random;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Periodic stock simulation: random stock fluctuation for shops with a
 * fluctuation section, and stock stabilizing toward the median. Runs every
 * in-game 30 minutes from the plugin's periodic task.
 *
 * <p>State lives in {@link ShopRegistry}. {@code getInstance()} is a
 * migration scaffold toward constructor injection.</p>
 */
public final class ShopStockService
{
    private static final ShopStockService INSTANCE = new ShopStockService(ShopRegistry.getInstance());

    public static ShopStockService getInstance()
    {
        return INSTANCE;
    }

    private final ShopRegistry registry;

    public ShopStockService(ShopRegistry registry)
    {
        this.registry = registry;
    }

    private int randomStockTimer = 1;
    public void randomChange(Random generator)
    {
        boolean legacyStabilizer = ConfigUtil.GetUseLegacyStockStabilization();

        // 인게임 30분마다 실행됨 (500틱)
        randomStockTimer += 1;
        if (randomStockTimer >= Integer.MAX_VALUE)
        {
            randomStockTimer = 0;
        }
        //DynamicShop.console.sendMessage("debug... " + randomStockTimer);

        for(Map.Entry<String, CustomConfig> entry : registry.entries())
        {
            boolean somethingIsChanged = false;

            CustomConfig data = entry.getValue();

            // fluctuation
            ConfigurationSection confSec = data.get().getConfigurationSection("Options.fluctuation");
            if (confSec != null)
            {
                int interval = confSec.getInt("interval");

                if (interval < 1 || interval > 999)
                {
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " Wrong value at " + entry.getKey() + ".Options.fluctuation.interval");
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " Reset to 48");
                    confSec.set("interval", 48);
                    interval = 48;
                }

                if (randomStockTimer % interval != 0) continue;

                for (String item : data.get().getKeys(false))
                {
                    try
                    {
                        int i = Integer.parseInt(item); // options에 대해 적용하지 않기 위해.
                        if (!data.get().contains(item + ".value")) continue; // 장식용은 스킵

                        int stock = data.get().getInt(item + ".stock");
                        if (stock <= 1) continue; // 무한재고에 대해서는 스킵
                        int median = data.get().getInt(item + ".median");
                        if (median <= 1) continue; // 고정가 상품에 대해서는 스킵

                        stock = RandomStockFluctuation(generator, stock, median, confSec.getDouble("strength"));

                        data.get().set(item + ".stock", stock);
                        somethingIsChanged = true;
                    } catch (Exception ignored)
                    {
                    }
                }
            }

            // stock stabilizing
            ConfigurationSection confSec2 = data.get().getConfigurationSection("Options.stockStabilizing");
            if (confSec2 != null)
            {
                int interval = confSec2.getInt("interval");

                if (interval < 1 || interval > 999)
                {
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " Wrong value at " + entry.getKey() + ".Options.stockStabilizing.interval");
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + " Reset to 48");
                    confSec2.set("interval", 48);
                    interval = 48;
                }

                //DynamicShop.console.sendMessage("debug... " + randomStockTimer + " % " + interval + " = " + randomStockTimer % interval);

                if (randomStockTimer % interval != 0) continue;

                for (String item : data.get().getKeys(false))
                {
                    try
                    {
                        int i = Integer.parseInt(item); // options에 대해 적용하지 않기 위해.
                        if (!data.get().contains(item + ".value")) continue; // 장식용은 스킵

                        int stock = data.get().getInt(item + ".stock");
                        if (stock < 1) continue; // 무한재고에 대해서는 스킵
                        int median = data.get().getInt(item + ".median");
                        if (median < 1) continue; // 고정가 상품에 대해서는 스킵

                        if (stock == median)
                            continue; // 이미 같으면 스킵

                        stock = StockStabilizing(legacyStabilizer, generator, stock, median, confSec2.getDouble("strength"));

                        data.get().set(item + ".stock", stock);
                        somethingIsChanged = true;
                    } catch (Exception ignored)
                    {
                    }
                }
            }

            if(somethingIsChanged)
                data.save();
        }
    }

    public int RandomStockFluctuation(Random generator, int stock, int median, double strength)
    {
        boolean down = generator.nextBoolean();
        double rate = stock / (double)median;
        if(rate < 0.5 && generator.nextBoolean())
            down = false;
        else if(rate > 2 && generator.nextBoolean())
            down = true;

        int amount = (int)(median * (strength / 100.0) * generator.nextFloat());
        if(down)
            amount *= -1;

        stock += amount;
        if (stock < 2) stock = 2;

        return stock;
    }

    public int StockStabilizing(Boolean isLegacyMode, Random generator,int stock, int median, double strength)
    {
        if (isLegacyMode)
        {
            int amount = (int)(median * (strength / 100.0));
            if (stock < median)
            {
                stock += amount;
                if (stock > median) stock = median;
            } else
            {
                stock -= amount;
                if (stock < median) stock = median;
            }
        }
        else
        {
            int amount = (int)((median - stock) * (strength / 100.0));
            if (amount == 0)
            {
                if (generator.nextInt() % 2 == 0)
                {
                    amount = (stock > median) ? -1 : 1;
                }
            }

            stock += amount;
        }

        return stock;
    }
}
