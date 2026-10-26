package me.sat7.dynamicshop.utilities;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LangUtil
{
    public static CustomConfig ccLang = new CustomConfig();

    private LangUtil()
    {

    }

    public static void setupLangFile(String lang)
    {
        // 한국어
        {
            ccLang.setup("Lang_V3_ko-KR", null);
            addLangDefaults("lang/Lang_V3_ko-KR.yml");
            ccLang.get().options().copyDefaults(true);
            ccLang.save();
        }

        // 영어
        {
            ccLang.setup("Lang_V3_en-US", null);
            addLangDefaults("lang/Lang_V3_en-US.yml");
            ccLang.get().options().copyDefaults(true);
            ccLang.save();
        }

        if (lang == null) lang = "en-US";

        if (!lang.equals("en-US") && !lang.equals("ko-KR"))
        {
            ConfigurationSection conf = ccLang.get().getConfigurationSection("");

            ccLang.setup("Lang_V3_" + lang, null);

            for (String s : conf.getKeys(true))
            {
                if (!ccLang.get().contains(s))
                {
                    DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + "String Key " + s + " added");
                    ccLang.get().addDefault(s, conf.get(s));
                }
            }
        } else
        {
            ccLang.setup("Lang_V3_" + lang, null);
        }

        ccLang.get().options().copyDefaults(true);
        ccLang.save();

        ReloadNumberFormat();
    }

    // lang 폴더에 번들된 기본 다국어 데이터를 언어 파일에 병합한다. (유저가 수정한 기존 값은 유지)
    private static void addLangDefaults(String resourcePath)
    {
        InputStream in = LangUtil.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null)
        {
            DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + "Lang resource not found: " + resourcePath);
            return;
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));

        for (String key : defaults.getKeys(true))
        {
            if (!defaults.isConfigurationSection(key))
            {
                ccLang.get().addDefault(key, defaults.get(key));
            }
        }
    }

    public static final Pattern HEX_PATTERN = Pattern.compile("(#[A-Fa-f0-9]{6})");

    public static String t(Player player, String key)
    {
        return t(player, key, true);
    }

    public static String t(CommandSender sender, String key)
    {
        Player player = null;
        if(sender instanceof Player)
            player = (Player) sender;

        return t(player, key, true);
    }

    public static String t(Player player, String key, boolean hexConvert)
    {
        String temp = ccLang.get().getString(key);
        if(temp == null || temp.isEmpty())
            return key;

        if (hexConvert && ConfigUtil.GetUseHexColorCode())
        {
            Matcher matcher = HEX_PATTERN.matcher(temp);
            while (matcher.find())
            {
                temp = temp.replace(matcher.group(), String.valueOf(ChatColor.of(matcher.group())));
            }
        }

        if(player != null && DynamicShop.isPapiExist && ConfigUtil.GetUsePlaceholderAPI())
            return PlaceholderAPI.setPlaceholders(player, temp);
        else
            return temp;
    }

    public static String TranslateHexColor(String message)
    {
        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find())
        {
            message = message.replace(matcher.group(), String.valueOf(ChatColor.of(matcher.group())));
        }
        return message;
    }

    public static boolean sendMessageWithLocalizedItemName(Player player, String message, Material material) {
        if (material != null) {
            String matKey;
            try {
                matKey = material.translationKey();
            } catch (Exception var8) {
                Bukkit.getLogger().severe("[DynamicShop] Unable to query Material: " + material.name());
                return false;
            }

            String[] splitByRegex = null;
            if(ConfigUtil.GetUseHexColorCode())
                splitByRegex = HEX_PATTERN.split(message);

            if(splitByRegex != null && splitByRegex.length > 1)
            {
                StringBuilder finalString;
                if(splitByRegex[0].contains("<item>"))
                {
                    String[] splitByItem = splitByRegex[0].split("<item>");
                    finalString = new StringBuilder(("{\"text\":\"" + splitByItem[0] + "\"},"));
                    finalString.append("{\"translate\":\"").append(matKey).append("\"},");
                    finalString.append("{\"text\":\"").append(splitByItem[1]).append("\"},");
                }
                else
                {
                    finalString = new StringBuilder("{\"text\":\"" + splitByRegex[0] + "\"},");
                }

                int idx = 0;

                Matcher matcher = HEX_PATTERN.matcher(message);

                while (matcher.find())
                {
                    if(splitByRegex[idx+1].contains("<item>"))
                    {
                        String[] splitByItem = splitByRegex[idx+1].split("<item>");
                        finalString.append("{\"text\":\"").append(splitByItem[0]).append("\", \"color\":\"").append(matcher.group()).append("\"},");
                        finalString.append("{\"translate\":\"").append(matKey).append("\", \"color\":\"").append(matcher.group()).append("\"},");
                        finalString.append("{\"text\":\"").append(splitByItem[1]).append("\", \"color\":\"").append(matcher.group()).append("\"}");
                    }
                    else
                    {
                        finalString.append("{\"text\":\"").append(splitByRegex[idx + 1]).append("\", \"color\":\"").append(matcher.group()).append("\"}");
                    }

                    idx++;
                    if(idx < splitByRegex.length - 1)
                        finalString.append(",");
                }

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " [" + finalString + "]");
            }
            else
            {
                String replacement = "\",{\"translate\":\"" + matKey + "\"";

                String text = message.split("<item>")[0];
                if (text.contains("§")) {
                    String colorCode = org.bukkit.ChatColor.getLastColors(text).replace("§", "");
                    if (org.bukkit.ChatColor.getByChar(colorCode) != null) {
                        String colorName = org.bukkit.ChatColor.getByChar(colorCode).name();
                        replacement = replacement + ", \"color\":\"" + colorName.toLowerCase() + "\"";
                    }
                }
                replacement = replacement + "},\"";

                String msg = message.replace("<item>", replacement);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " [\"" + msg + "\"]");
            }

            return true;
        } else {
            return false;
        }
    }

    private static void ReloadNumberFormat()
    {
        intFormat = new DecimalFormat(ConfigUtil.GetIntFormat());
        doubleFormat = new DecimalFormat(ConfigUtil.GetDoubleFormat());
    }

    private static DecimalFormat intFormat;
    private static DecimalFormat doubleFormat;

    public static String n(int i)
    {
        return intFormat.format(i);
    }

    public static String n(double i)
    {
        return doubleFormat.format(i);
    }

    public static String n(double i, boolean toInt)
    {
        if (toInt)
            return intFormat.format((int)i);
        else
            return doubleFormat.format(i);
    }
}
