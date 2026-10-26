package me.sat7.dynamicshop.utilities;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden test that locks in the worth seed data extracted from the original
 * hardcoded {@code WorthUtil.setupWorthFile()} addDefault calls into a bundle
 * resource (worth/Worth_V2_defaults.yml). The fixture (golden/worth/Worth_V2.yml)
 * is the file the pre-refactoring build actually generated on a live server, so
 * the regenerated output must match byte for byte: header comments, key order
 * and value formatting. Also guards against Maven resource filtering corrupting
 * the bundled YAML, since tests read the processed copy under target/classes.
 */
class WorthResourceGoldenTests
{
    private static final String HEADER =
            "This data is only used to calculate recommendations.\nIt does not affect the value of items in the store.";

    @Test
    @DisplayName("bundled worth resource regenerates the server-generated golden file")
    void resourceRegeneratesGoldenFile()
    {
        YamlConfiguration resource = load("worth/Worth_V2_defaults.yml");

        YamlConfiguration regenerated = new YamlConfiguration();
        regenerated.options().header(HEADER);

        int count = 0;
        for (String key : resource.getKeys(false))
        {
            regenerated.addDefault(key, resource.getDouble(key));
            count++;
        }

        assertTrue(count >= 1200, "worth resource looks wrong: only " + count + " entries");

        regenerated.options().copyDefaults(true);

        String golden = loadText("golden/worth/Worth_V2.yml");
        assertEquals(normalize(golden), normalize(regenerated.saveToString()));
    }

    /** Loads a YAML file from the classpath as UTF-8. */
    private static YamlConfiguration load(String resource)
    {
        try (InputStreamReader reader = new InputStreamReader(
                WorthResourceGoldenTests.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8))
        {
            return YamlConfiguration.loadConfiguration(reader);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static String loadText(String resource)
    {
        try (Scanner scanner = new Scanner(
                Paths.get(WorthResourceGoldenTests.class.getClassLoader().getResource(resource).toURI()), "UTF-8"))
        {
            return scanner.useDelimiter("\\A").next();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String s)
    {
        return s.replace("\r\n", "\n");
    }
}
