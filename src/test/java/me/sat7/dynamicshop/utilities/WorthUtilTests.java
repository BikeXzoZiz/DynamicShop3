package me.sat7.dynamicshop.utilities;

import me.sat7.dynamicshop.files.CustomConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the worth lookup behaviour used for price recommendations: raw key
 * first, then a normalized retry (dashes/underscores stripped, lowercased).
 * The same normalization is applied to the item name shown in the
 * "no recommend data" message, which these tests cover via
 * {@link WorthUtil#normalizeWorthKey}.
 */
class WorthUtilTests
{
    private static final String KEY = "diamond";
    private static final double EPS = 1e-9;

    /** Injects an in-memory FileConfiguration without disk access or a Bukkit server. */
    private static class InMemoryConfig extends CustomConfig
    {
        InMemoryConfig(FileConfiguration config)
        {
            this.customFile = config;
        }
    }

    private CustomConfig original;
    private FileConfiguration worthYml;

    @BeforeEach
    void injectWorthConfig()
    {
        original = WorthUtil.ccWorth;
        worthYml = new YamlConfiguration();
        WorthUtil.ccWorth = new InMemoryConfig(worthYml);
    }

    @AfterEach
    void restoreWorthConfig()
    {
        WorthUtil.ccWorth = original;
    }

    @Test
    @DisplayName("raw key that exists is returned as-is")
    void rawKeyFound()
    {
        worthYml.set(KEY, 550.0);
        assertEquals(550.0, WorthUtil.getRecommendedWorth(KEY), EPS);
    }

    @Test
    @DisplayName("keys with dashes/underscores/mixed case fall back to the normalized name")
    void normalizedFallback()
    {
        worthYml.set("stoneaxe", 5.32);
        assertEquals(5.32, WorthUtil.getRecommendedWorth("STONE_AXE"), EPS);
        assertEquals(5.32, WorthUtil.getRecommendedWorth("Stone-Axe"), EPS);
    }

    @Test
    @DisplayName("keys that only exist verbatim are not normalized away (raw wins)")
    void rawWinsOverNormalized()
    {
        worthYml.set("potion{Potion:healing}", 29.69);
        worthYml.set("potion{potion:healing}", 1.0);
        assertEquals(29.69, WorthUtil.getRecommendedWorth("potion{Potion:healing}"), EPS);
    }

    @Test
    @DisplayName("missing data returns 0 and the message shows the normalized key")
    void missingReturnsZero()
    {
        assertEquals(0.0, WorthUtil.getRecommendedWorth("MYSTERY_BLOCK"), EPS);
        assertEquals("mysteryblock", WorthUtil.normalizeWorthKey("MYSTERY_BLOCK"));
    }
}
