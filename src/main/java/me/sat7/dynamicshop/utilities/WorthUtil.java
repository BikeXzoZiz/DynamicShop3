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

    // worth 데이터 조회. 없으면 재질 이름의 '-'와 '_'를 제거하고 소문자로 바꿔서 다시 조회함.
    public static double getRecommendedWorth(String itemName)
    {
        double worth = ccWorth.get().getDouble(itemName);
        if (worth == 0)
            worth = ccWorth.get().getDouble(normalizeWorthKey(itemName));

        return worth;
    }

    // 추천 가격 데이터가 없을 때 안내문에 표시할 조회용 키 (정규화된 이름).
    public static String normalizeWorthKey(String itemName)
    {
        return itemName.replace("-", "").replace("_", "").toLowerCase();
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
