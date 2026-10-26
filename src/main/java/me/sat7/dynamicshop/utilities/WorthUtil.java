package me.sat7.dynamicshop.utilities;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorthUtil
{
    public static CustomConfig ccWorth = new CustomConfig();

    private WorthUtil()
    {

    }

    public static void setupWorthFile()
    {
        ccWorth.setup("Worth_V2", null);
        ccWorth.get().options().header("This data is only used to calculate recommendations.\nIt does not affect the value of items in the store.");
        addWorthDefaults("worth/Worth_V2_defaults.yml");
        ccWorth.get().options().copyDefaults(true);
        ccWorth.save();
    }

    // 번들된 기본 worth 데이터를 Worth_V2 파일에 병합한다. (유저가 수정한 기존 값은 유지)
    private static void addWorthDefaults(String resourcePath)
    {
        InputStream in = WorthUtil.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null)
        {
            DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + "Worth resource not found: " + resourcePath);
            return;
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));

        for (String key : defaults.getKeys(false))
        {
            ccWorth.get().addDefault(key, defaults.getDouble(key));
        }
    }
}
