package me.sat7.dynamicshop.transactions;

import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.utilities.ShopUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden tests that lock in the exact pricing behaviour of the original
 * DynamicShop3 engine ({@link Calc}) before refactoring. Expectations were
 * derived from the original implementation, including its quirks. If a test
 * fails after a change, the change altered observable pricing behaviour and
 * must be justified.
 */
class CalcGoldenTests
{
    private static final String SHOP = "testshop";
    private static final String ITEM = "1";
    private static final double EPS = 1e-9;

    /** Injects an in-memory FileConfiguration without disk access or a Bukkit server. */
    private static class InMemoryConfig extends CustomConfig
    {
        InMemoryConfig(FileConfiguration config)
        {
            this.customFile = config;
        }
    }

    private FileConfiguration shopYml;

    @BeforeEach
    void registerShop()
    {
        shopYml = new YamlConfiguration();
        ShopUtil.shopConfigFiles.put(SHOP, new InMemoryConfig(shopYml));
    }

    @AfterEach
    void unregisterShop()
    {
        ShopUtil.shopConfigFiles.remove(SHOP);
    }

    // ---------- fixed price (median <= 0 or stock <= 0) ----------

    @Test
    @DisplayName("Fixed price: buy price equals configured value")
    void fixedPrice_buyReturnsValue()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".median", -1);
        shopYml.set(ITEM + ".stock", -1);

        assertEquals(10.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    @Test
    @DisplayName("Fixed price: sell price has tax subtracted when no explicit value2")
    void fixedPrice_sellAppliesTax()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".stock", 0); // stock <= 0 -> fixed price
        shopYml.set("Options.SalesTax", 25);

        assertEquals(7.5, Calc.getCurrentPrice(SHOP, ITEM, false), EPS);
    }

    // ---------- dynamic pricing (price = median * value / stock) ----------

    @Test
    @DisplayName("Dynamic: buy price = median * value / stock")
    void dynamic_buyPrice()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 5);

        // (100*10)/5 = 200
        assertEquals(200.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    @Test
    @DisplayName("Dynamic: sell price uses stock+1 (built-in spread), then tax")
    void dynamic_sellPriceUsesStockPlusOne()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 5);
        shopYml.set("Options.SalesTax", 25);

        // (100*10)/(5+1) = 166.6667, minus 25% tax = 125.0
        assertEquals(125.0, Calc.getCurrentPrice(SHOP, ITEM, false), EPS);
    }

    // ---------- bounds ----------

    @Test
    @DisplayName("Dynamic: price is clamped up to valueMin (default 0.0001)")
    void minClamp_defaultValueMin()
    {
        shopYml.set(ITEM + ".value", 0.001);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 1000000);

        // 100*0.001/1000000 = 1e-7 -> clamped to default min
        assertEquals(0.0001, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    @Test
    @DisplayName("Dynamic: price is clamped down to valueMax")
    void maxClamp_valueMax()
    {
        shopYml.set(ITEM + ".value", 1000.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 1);
        shopYml.set(ITEM + ".valueMax", 500.0);

        assertEquals(500.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    @Test
    @DisplayName("Bounds: valueMax = 0 (or absent) means unbounded - quirk")
    void maxZeroMeansUnbounded()
    {
        shopYml.set(ITEM + ".value", 1000.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 1);
        shopYml.set(ITEM + ".valueMax", 0.0);

        assertEquals(100000.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    // ---------- discount / explicit sell value ----------

    @Test
    @DisplayName("Discount applies to buy price (price * (100-discount)/100)")
    void discount_appliesToBuy()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set(ITEM + ".discount", 50);

        assertEquals(5.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
    }

    @Test
    @DisplayName("Discount applies before tax on sell")
    void discount_appliesBeforeTaxOnSell()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set(ITEM + ".discount", 50);
        shopYml.set("Options.SalesTax", 25);

        // 10 * 0.5 = 5, minus 25% tax = 3.75
        assertEquals(3.75, Calc.getCurrentPrice(SHOP, ITEM, false), EPS);
    }

    @Test
    @DisplayName("value2: explicit sell value, tax is NOT applied")
    void value2_explicitSellPriceNoTax()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".value2", 6.0);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set("Options.SalesTax", 25);

        assertEquals(6.0, Calc.getCurrentPrice(SHOP, ITEM, false), EPS);
        assertEquals(6.0, Calc.getCurrentPrice(SHOP, ITEM, false, true), EPS);

        double[] result = Calc.calcTotalCost(SHOP, ITEM, -3);
        assertEquals(18.0, result[0], EPS);
        assertEquals(0.0, result[1], EPS);
    }

    // ---------- integer-only shops ----------

    @Test
    @DisplayName("integeronly: buy prices ceil, sell prices floor, raw bypasses it")
    void integerOnly_ceilBuyFloorSell()
    {
        shopYml.set(ITEM + ".value", 9.2);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set("Options.flag.integeronly", true);

        assertEquals(10.0, Calc.getCurrentPrice(SHOP, ITEM, true), EPS);
        assertEquals(9.0, Calc.getCurrentPrice(SHOP, ITEM, false), EPS);
        assertEquals(9.2, Calc.getCurrentPrice(SHOP, ITEM, true, true), EPS);
    }

    @Test
    @DisplayName("integeronly bulk: buys ceil the total, sells floor it")
    void integerOnly_bulkTotals()
    {
        shopYml.set(ITEM + ".value", 9.2);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set("Options.flag.integeronly", true);

        assertEquals(28.0, Calc.calcTotalCost(SHOP, ITEM, 3)[0], EPS); // ceil(27.6)
        assertEquals(27.0, Calc.calcTotalCost(SHOP, ITEM, -3)[0], EPS); // floor(27.6)
    }

    // ---------- bulk totals & slippage ----------

    @Test
    @DisplayName("Bulk buy: each unit is priced at the stock level reached (slippage)")
    void bulkBuy_slippage()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 5);

        // units: 1000/5 (=200) + 1000/4 (=250) + 1000/3 (=333.3333...)
        assertEquals(783.3333, Calc.calcTotalCost(SHOP, ITEM, 3)[0], EPS);
    }

    @Test
    @DisplayName("Quirk: buying down to stock<2 silently drops the remaining units")
    void bulkBuy_quirk_dropsUnitsWhenStockBelowTwo()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".median", 100);
        shopYml.set(ITEM + ".stock", 3);

        // Buying 3 from stock 3 charges only 2 units: after the 2nd unit stock
        // reaches 1 (<2) and the loop breaks. Documented original behaviour.
        assertEquals(833.3333, Calc.calcTotalCost(SHOP, ITEM, 3)[0], EPS);
    }

    // ---------- tax ----------

    @Test
    @DisplayName("calcTotalCost: sell tax returned as second element and subtracted from total")
    void sellTax_totalAndTaxComponent()
    {
        shopYml.set(ITEM + ".value", 10.0);
        shopYml.set(ITEM + ".stock", -1);
        shopYml.set("Options.SalesTax", 25);

        double[] sell = Calc.calcTotalCost(SHOP, ITEM, -4);
        assertEquals(30.0, sell[0], EPS);
        assertEquals(10.0, sell[1], EPS);

        double[] buy = Calc.calcTotalCost(SHOP, ITEM, 4);
        assertEquals(40.0, buy[0], EPS);
        assertEquals(0.0, buy[1], EPS);
    }

    @Test
    @DisplayName("getTaxRate: shop-level SalesTax wins over the global default (0 in tests)")
    void taxRate_shopLevelOverridesGlobal()
    {
        shopYml.set("Options.SalesTax", 15);
        assertEquals(15, Calc.getTaxRate(SHOP));

        shopYml.set("Options.SalesTax", null);
        // No plugin config loaded in tests -> global currentTax defaults to 0.
        assertEquals(0, Calc.getTaxRate(SHOP));
    }
}
