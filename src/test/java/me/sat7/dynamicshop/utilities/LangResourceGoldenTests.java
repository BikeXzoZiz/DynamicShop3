package me.sat7.dynamicshop.utilities;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden tests that lock in the exact message data extracted from the original
 * hardcoded {@code LangUtil.setupLangFile()} into bundle resources (lang/*.yml).
 * Fixtures (golden/lang/*.yml) are the files the pre-refactoring build actually
 * generated on a live server, so key order and values must match exactly.
 * Also guards against Maven resource filtering corrupting the bundled YAML,
 * since tests read the processed copy under target/classes.
 */
class LangResourceGoldenTests
{
    @Test
    @DisplayName("ko-KR: bundled resource equals server-generated golden file")
    void koKR_resourceMatchesGeneratedFile()
    {
        assertSameAsGolden("Lang_V3_ko-KR");
    }

    @Test
    @DisplayName("en-US: bundled resource equals server-generated golden file")
    void enUS_resourceMatchesGeneratedFile()
    {
        assertSameAsGolden("Lang_V3_en-US");
    }

    private void assertSameAsGolden(String lang)
    {
        YamlConfiguration resource = load("lang/" + lang + ".yml");
        YamlConfiguration golden = load("golden/lang/" + lang + ".yml");

        List<String> resourceKeys = leafKeys(resource);
        List<String> goldenKeys = leafKeys(golden);

        assertTrue(goldenKeys.size() >= 400, "golden fixture looks wrong: only " + goldenKeys.size() + " messages");
        assertEquals(goldenKeys, resourceKeys, lang + ": key set/order differs from golden fixture");

        for (String key : goldenKeys)
        {
            assertEquals(golden.getString(key), resource.getString(key), lang + " value mismatch: " + key);
        }
    }

    /** Loads a YAML file from the classpath as UTF-8. */
    private static YamlConfiguration load(String resource)
    {
        try (InputStreamReader reader = new InputStreamReader(
                LangResourceGoldenTests.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8))
        {
            return YamlConfiguration.loadConfiguration(reader);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /** Leaf keys in document (insertion) order, mirroring LangUtil's addDefault iteration. */
    private static List<String> leafKeys(YamlConfiguration config)
    {
        List<String> leaves = new ArrayList<>();
        for (String key : config.getKeys(true))
        {
            if (!config.isConfigurationSection(key))
                leaves.add(key);
        }
        return leaves;
    }
}
