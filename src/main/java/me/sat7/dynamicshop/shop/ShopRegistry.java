package me.sat7.dynamicshop.shop;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.utilities.ConfigUtil;

/**
 * 단일 소유자(single owner) of the in-memory shop file registry and its
 * persistence lifecycle.
 *
 * <p>Owns the {@code Shop/*.yml} config map and the per-shop dirty flags.
 * Loads every shop file on boot, tracks unsaved changes, and performs the
 * periodic/forced saves and YML backups. All shop registration changes
 * (create / rename / copy / merge / delete) go through this class.</p>
 *
 * <p>{@code ShopUtil.shopConfigFiles} and {@code ShopUtil.shopDirty} are
 * legacy aliases to the same live maps, kept while callers are migrated
 * slice by slice. New code should use the registry, not the aliases.</p>
 *
 * <p>{@code getInstance()} is a migration scaffold toward constructor
 * injection; the composition root will construct and pass this class
 * explicitly once the static singletons are gone.</p>
 */
public final class ShopRegistry
{
    private static final ShopRegistry INSTANCE = new ShopRegistry();

    public static ShopRegistry getInstance()
    {
        return INSTANCE;
    }

    private final HashMap<String, CustomConfig> shopConfigFiles = new HashMap<>();
    private final HashMap<String, Boolean> shopDirty = new HashMap<>();

    private ShopRegistry()
    {

    }

    // ---------- live map accessors (migration scaffold, do not use in new code) ----------

    public HashMap<String, CustomConfig> configFiles()
    {
        return shopConfigFiles;
    }

    public HashMap<String, Boolean> dirtyFlags()
    {
        return shopDirty;
    }

    // ---------- registration ----------

    // 상점 파일을 레지스트리에 등록. (클린 상태로 등록됨)
    public void register(String shopName, CustomConfig config)
    {
        shopConfigFiles.put(shopName, config);
        shopDirty.put(shopName, false);
    }

    // 상점 이름 변경: 새 이름으로 재등록하고 옛 등록을 제거함.
    public void renameRegistration(String oldName, String newName, CustomConfig config)
    {
        shopConfigFiles.put(newName, config);
        shopConfigFiles.remove(oldName);
        shopDirty.put(newName, false);
        shopDirty.remove(oldName);
    }

    // 상점 복제: 새 이름으로 등록. (파일 복사는 호출측에서 처리)
    public void copyRegistration(String newName, CustomConfig config)
    {
        shopConfigFiles.put(newName, config);
        shopDirty.put(newName, false);
    }

    // 상점 등록 해제. (파일 삭제는 호출층에서 처리)
    public void unregister(String shopName)
    {
        shopConfigFiles.remove(shopName);
        shopDirty.remove(shopName);
    }

    // 상점에 저장되지 않은 변경이 있음을 표시.
    public void markDirty(String shopName)
    {
        shopDirty.put(shopName, true);
    }

    // ---------- lifecycle ----------

    // Shop 폴더의 모든 yml을 다시 읽어들임. 기존 메모리 상태는 전부 버려짐.
    public void loadAll()
    {
        shopConfigFiles.clear();
        shopDirty.clear();

        File[] listOfFiles = new File(DynamicShop.plugin.getDataFolder() + "/Shop").listFiles();
        if(listOfFiles != null)
        {
            for (File f : listOfFiles)
            {
                CustomConfig shopCC = new CustomConfig();

                int idx = f.getName().lastIndexOf( "." );
                String shopName = f.getName().substring(0, idx );
                shopCC.setup(shopName, "Shop");
                register(shopName, shopCC);
            }
        }
    }

    // 첫 부팅시 샘플 상점 파일 생성.
    public void setupSampleShop()
    {
        if(shopConfigFiles.isEmpty())
        {
            CustomConfig data = new CustomConfig();
            data.setup("SampleShop", "Shop");

            data.get().options().header("Shop name can not contain formatting codes, '/' and ' '");
            data.get().options().copyHeader(true);

            data.get().set("Options.title", "Sample Shop");
            data.get().set("Options.lore", "This is sample shop");
            data.get().set("Options.permission", "");
            data.get().set("Options.page", 2);
            data.get().set("Options.currency", Constants.S_VAULT);
            data.get().set("0.mat", "DIRT");
            data.get().set("0.value", 1);
            data.get().set("0.median", 10000);
            data.get().set("0.stock", 10000);
            data.get().set("1.mat", "COBBLESTONE");
            data.get().set("1.value", 1.5);
            data.get().set("1.median", 10000);
            data.get().set("1.stock", 10000);

            register("SampleShop", data);

            data.get().options().copyDefaults(true);
            data.save();
        }
    }

    // Shop.yml 한덩어리로 되있는 데이터를 새 버전 방식으로 변환함 (v2 -> v3)
    public void convertOldShopData()
    {
        File file = new File(DynamicShop.plugin.getDataFolder(), "Shop.yml");
        if (file.exists())
        {
            CustomConfig oldShopData = new CustomConfig();
            oldShopData.setup("Shop", null);

            for(String oldShopName : oldShopData.get().getKeys(false))
            {
                ConfigurationSection oldData = oldShopData.get().getConfigurationSection(oldShopName);

                CustomConfig data = new CustomConfig();
                data.setup(oldShopName, "Shop");

                for(String s : oldData.getKeys(false))
                {
                    data.get().set(s, oldData.get(s));
                }

                if(data.get().contains("Options.hideStock"))
                {
                    data.get().set("Options.flag.hidestock", "");
                    data.get().set("Options.hideStock", null);
                }
                if(data.get().contains("Options.hidePricingType"))
                {
                    data.get().set("Options.flag.hidepricingtype", "");
                    data.get().set("Options.hidePricingType", null);
                }
                if(!data.get().contains("Options.lore"))
                {
                    data.get().set("Options.lore","");
                }

                data.save();

                register(oldShopName, data);
            }

            file.delete();
        }
    }

    // 구버전 상점 yml 스키마를 현재 버전으로 마이그레이션함.
    public void applyBackwardCompatibility()
    {
        int userVersion = ConfigUtil.GetConfigVersion();
        if (userVersion < 5)
        {
            for(Map.Entry<String, CustomConfig> entry : shopConfigFiles.entrySet())
            {
                FileConfiguration fc = entry.getValue().get();
                for(String key : fc.getKeys(false))
                {
                    if (fc.contains(key + ".tradeLimitPerPlayer.value"))
                    {
                        int old = fc.getInt(key + ".tradeLimitPerPlayer.value");
                        if (old < 0)
                        {
                            fc.set(key + ".tradeLimitPerPlayer.sell", old * -1);
                        }
                        else if (old > 0)
                        {
                            fc.set(key + ".tradeLimitPerPlayer.buy", old);
                        }

                        fc.set(key + ".tradeLimitPerPlayer.value", null);
                    }
                }
            }
        }

        if (userVersion < 7)
        {
            for(Map.Entry<String, CustomConfig> entry : shopConfigFiles.entrySet())
            {
                FileConfiguration fc = entry.getValue().get();
                if (fc.contains("Options.flag.jobpoint"))
                {
                    fc.set("Options.flag.jobpoint", null);
                    fc.set("Options.currency", Constants.S_JOBPOINT);
                }
                if (fc.contains("Options.flag.playerpoint"))
                {
                    fc.set("Options.flag.playerpoint", null);
                    fc.set("Options.currency", Constants.S_PLAYERPOINT);
                }

                if (!fc.contains("Options.currency"))
                {
                    fc.set("Options.currency", Constants.S_VAULT);
                }
            }
        }
    }

    // 변경된 상점 파일만 저장하고 dirty 플래그를 해제함.
    public void saveDirtyShops()
    {
        for(Map.Entry<String, Boolean> entry : shopDirty.entrySet())
        {
            if(entry.getValue())
            {
                if(DynamicShop.DEBUG_MODE)
                {
                    DynamicShop.console.sendMessage("DirtyShop Saved: " + entry.getValue());
                }

                shopDirty.put(entry.getKey(), false);
                shopConfigFiles.get(entry.getKey()).save();
            }
        }
    }

    // 모든 상점 파일을 강제로 저장함.
    public void forceSaveAll()
    {
        for(Map.Entry<String, CustomConfig> entry : shopConfigFiles.entrySet())
        {
            entry.getValue().save();
        }
    }

    // Shop 폴더의 yml들을 타임스탬프 백업 폴더로 복사하고 오래된 백업을 정리함.
    public void backupShopYmls()
    {
        File[] listOfFiles = new File(DynamicShop.plugin.getDataFolder() + "/Shop").listFiles();
        if(listOfFiles != null)
        {
            long time = System.currentTimeMillis();
            for (File f : listOfFiles)
            {
                String path = DynamicShop.plugin.getDataFolder() + "/Shop_Backup/" + time + "/";
                File newFile = new File(path + f.getName());
                try
                {
                    FileUtils.copyFile(f, newFile);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        cullOldBackups();
    }

    // 설정된 보관 기간이 지난 백업 폴더를 삭제함.
    public void cullOldBackups()
    {
        File[] listOfFiles = new File(DynamicShop.plugin.getDataFolder() + "/Shop_Backup").listFiles();
        if(listOfFiles != null)
        {
            for(File f : listOfFiles)
            {
                int ageMins = (int) (System.currentTimeMillis() - f.lastModified()) / 60000;
                if (ageMins >= ConfigUtil.GetShopYmlBackup_CullAgeMinutes())
                {
                    try
                    {
                        FileUtils.deleteDirectory(f);
                    } catch (IOException e)
                    {
                        //e.printStackTrace();
                    }
                }
            }
        }
    }
}
