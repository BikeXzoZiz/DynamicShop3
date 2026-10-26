package me.sat7.dynamicshop.transactions;

import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import me.sat7.dynamicshop.utilities.ShopUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Price engine of the shop.
 *
 * <p>Dynamic pricing: {@code price = median * value / stock}. More stock means a
 * cheaper price; every purchase lowers the stock and therefore raises the price
 * of the next unit (slippage). When {@code median <= 0} or {@code stock <= 0}
 * the item is traded at its fixed {@code value} instead.</p>
 *
 * <p>Sell prices use {@code stock + 1} (a one-step spread) unless an explicit
 * sell value ({@code value2}) is configured. Behaviour is locked down by
 * {@code CalcGoldenTests} - keep the floating-point operation order unchanged
 * or update the tests consciously.</p>
 */
public final class Calc
{
    /** Default lower bound for prices when a trade has no {@code valueMin}. */
    private static final double DEFAULT_MIN_PRICE = 0.0001;
    /** {@code valueMax == 0} means "no upper bound" in shop configs. */
    private static final double NO_MAX_PRICE = 0.0;
    /** Stock at or below which a bulk buy loop stops pricing further units (original quirk). */
    private static final int BUY_STOP_STOCK = 2;
    /** Result of getTaxRate() is a percent value. */
    private static final double PERCENT = 100.0;
    /** Totals are rounded to 4 decimal places when the shop is not integer-only. */
    private static final double ROUNDING_FACTOR = 10000.0;

    private Calc()
    {

    }

    /** Current unit price of a trade, with tax/rounding applied. */
    public static double getCurrentPrice(String shopName, String idx, boolean buy)
    {
        return getCurrentPrice(shopName, idx, buy, false);
    }

    /**
     * @param raw if true, skip the integer-only ceil/floor rounding
     * @return the price; {@code [0]} of {@link #calcTotalCost} is the taxed total
     */
    public static double getCurrentPrice(String shopName, String idx, boolean buy, boolean raw)
    {
        FileConfiguration data = shopData(shopName);

        double value;
        if (!buy && data.contains(idx + ".value2"))
        {
            value = data.getDouble(idx + ".value2");
        } else
        {
            value = data.getDouble(idx + ".value");
        }

        double min = data.getDouble(idx + ".valueMin", DEFAULT_MIN_PRICE);
        double max = data.getDouble(idx + ".valueMax");
        int median = data.getInt(idx + ".median");
        int stock = data.getInt(idx + ".stock");

        double price = unitPrice(value, median, stock, !buy);

        price = clamp(price, min, max);
        price = applyDiscountIfAny(data, idx, price);

        // Sales tax is only taken when there is no explicitly configured sell value.
        if (!buy && !data.contains(idx + ".value2"))
        {
            price -= price / PERCENT * getTaxRate(shopName);
        }

        if (!raw && data.contains("Options.flag.integeronly"))
        {
            return buy ? Math.ceil(price) : Math.floor(price);
        }
        return price;
    }

    /**
     * Total cost of the next {@code amount} units ({@code amount > 0} = buy,
     * {@code amount < 0} = sell). Each unit is priced at the stock level it is
     * traded at, so large orders slip in price.
     *
     * @return {@code [0]} = total (taxed, rounded), {@code [1]} = the tax part
     */
    public static double[] calcTotalCost(String shopName, String idx, int amount)
    {
        FileConfiguration data = shopData(shopName);

        int median = data.getInt(idx + ".median");
        int stock = data.getInt(idx + ".stock");

        double value;
        if (amount < 0 && data.contains(idx + ".value2"))
        {
            value = data.getDouble(idx + ".value2");
        } else
        {
            value = data.getDouble(idx + ".value");
        }

        double total = 0;
        if (median <= 0 || stock <= 0)
        {
            total = value * Math.abs(amount);
        } else
        {
            double min = data.getDouble(idx + ".valueMin", DEFAULT_MIN_PRICE);
            double max = data.getDouble(idx + ".valueMax");

            for (int i = 0; i < Math.abs(amount); i++)
            {
                if (amount < 0 && stock < Integer.MAX_VALUE)
                {
                    stock++;
                }
                total += clamp(median * value / stock, min, max);

                if (amount > 0)
                {
                    stock--;
                    if (stock < BUY_STOP_STOCK)
                    {
                        break;
                    }
                }
            }
        }

        total = applyDiscountIfAny(data, idx, total);

        // Sales tax is only taken when there is no explicitly configured sell value.
        double tax = 0;
        if (amount < 0 && !data.contains(idx + ".value2"))
        {
            tax = total / PERCENT * getTaxRate(shopName);
            total -= tax;
        }

        if (data.contains("Options.flag.integeronly"))
        {
            total = amount > 0 ? Math.ceil(total) : Math.floor(total);
        } else
        {
            total = Math.round(total * ROUNDING_FACTOR) / ROUNDING_FACTOR;
        }

        return new double[]{total, tax};
    }

    /** Shop-level sales tax rate in percent, falling back to the global config value. */
    public static int getTaxRate(String shopName)
    {
        CustomConfig data = ShopUtil.shopConfigFiles.get(shopName);
        if (data == null || data.get() == null)
        {
            throw new IllegalArgumentException("Unknown shop: " + shopName);
        }

        if (data.get().contains("Options.SalesTax"))
        {
            return data.get().getInt("Options.SalesTax");
        }
        return ConfigUtil.getCurrentTax();
    }

    /** Single-unit price before discount/tax; selling reads one stock step above buying. */
    private static double unitPrice(double value, int median, int stock, boolean selling)
    {
        if (median <= 0 || stock <= 0)
        {
            return value;
        }

        if (selling && stock < Integer.MAX_VALUE)
        {
            stock = stock + 1;
        }
        return (median * value) / stock;
    }

    private static double clamp(double price, double min, double max)
    {
        if (price < min)
        {
            price = min;
        }
        if (max != NO_MAX_PRICE && price > max)
        {
            price = max;
        }
        return price;
    }

    private static double applyDiscountIfAny(FileConfiguration data, String idx, double price)
    {
        if (data.contains(idx + ".discount"))
        {
            int discount = data.getInt(idx + ".discount");
            price = price * (PERCENT - discount) / PERCENT;
        }
        return price;
    }

    private static FileConfiguration shopData(String shopName)
    {
        CustomConfig config = ShopUtil.shopConfigFiles.get(shopName);
        if (config == null || config.get() == null)
        {
            throw new IllegalArgumentException("Unknown shop: " + shopName);
        }
        return config.get();
    }
}
