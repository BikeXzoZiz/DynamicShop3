package me.sat7.dynamicshop.shop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.constants.Constants;
import me.sat7.dynamicshop.files.CustomConfig;
import me.sat7.dynamicshop.utilities.ConfigUtil;
import me.sat7.dynamicshop.utilities.RotationUtil;
import me.sat7.dynamicshop.utilities.UserUtil;

import static me.sat7.dynamicshop.utilities.LangUtil.t;

/**
 * 단일 소유자(single owner) of the in-memory shop file registry and its
 * persistence lifecycle.
 *
 * <p>Owns the {@code Shop/*.yml} config map and the per-shop dirty flags.
 * Loads every shop file on boot, tracks unsaved changes, and performs the
 * periodic/forced saves and YML backups. All shop registration changes
 * (create / rename / copy / merge / delete) and shop-level data access
 * (balance, currency, per-slot commands) go through this class.</p>
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

    // ---------- read access ----------

    // 등록된 상점의 config. 없으면 null.
    public CustomConfig getShopConfig(String shopName)
    {
        return shopConfigFiles.get(shopName);
    }

    public boolean hasShop(String shopName)
    {
        return shopConfigFiles.containsKey(shopName);
    }

    // 모든 상점을 (이름, config) 쌍으로 순회할때 사용.
    public Set<Map.Entry<String, CustomConfig>> entries()
    {
        return shopConfigFiles.entrySet();
    }

    // 등록된 모든 상점 이름.
    public Set<String> shopNames()
    {
        return shopConfigFiles.keySet();
    }

    // 등록된 모든 상점 config.
    public Collection<CustomConfig> shops()
    {
        return shopConfigFiles.values();
    }

    // 등록된 상점 개수.
    public int count()
    {
        return shopConfigFiles.size();
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

    // ---------- shop-level operations ----------

    // 상점 이름 바꾸기
    public void renameShop(String shopName, String newName)
    {
        CustomConfig data = shopConfigFiles.get(shopName);
        if (data == null)
            return;

        data.rename(newName);
        data.get().set("Options.title", newName);
        renameRegistration(shopName, newName, data);

        RotationUtil.OnShopNameChanged(shopName, newName);
        UserUtil.OnRenameShop(shopName, newName);
    }

    public void copyShop(String shopName, String newName)
    {
        CustomConfig data = shopConfigFiles.get(shopName);
        if (data == null)
            return;

        data.copy(newName);
        data.get().set("Options.title", newName);
        copyRegistration(newName, data);

        RotationUtil.OnShopCopy(shopName, newName);
    }

    // 상점 병합
    public void mergeShop(String shopA, String shopB)
    {
        // A에 B의 아이템을 다 밀어넣는 방식임.
        // 상점잔액 합쳐주는것 말고는 별도의 처리 없음
        CustomConfig dataA = shopConfigFiles.get(shopA);
        CustomConfig dataB = shopConfigFiles.get(shopB);

        int pg1 = dataA.get().getInt("Options.page");
        int pg2 = dataB.get().getInt("Options.page");

        dataA.get().set("Options.page", pg1 + pg2);
        if (dataA.get().contains("Options.Balance") || dataB.get().contains("Options.Balance"))
        {
            double a = getShopBalance(shopA);
            if (a == -1) a = 0;

            double b = 0;
            if (!(dataA.get().getString("Options.Balance").equals(shopB) || dataB.get().getString("Options.Balance").equals(shopA)))
            {
                b = getShopBalance(shopB);
            }

            if (b == -1) b = 0;

            if (a + b > 0)
            {
                dataA.get().set("Options.Balance", a + b);
            } else
            {
                dataA.get().set("Options.Balance", null);
            }
        }

        for (String item : dataB.get().getKeys(false))
        {
            try
            {
                dataA.get().set(String.valueOf(Integer.parseInt(item) + (pg1 * 45)), dataB.get().get(item));
            } catch (Exception ignored)
            {
            }
        }

        UserUtil.OnMergeShop(shopA, shopB);

        dataB.delete();
        unregister(shopB);

        dataA.save();
        dataA.reload();
    }

    // 상점의 잔액 확인
    public double getShopBalance(String shopName)
    {
        CustomConfig data = shopConfigFiles.get(shopName);
        if (data == null)
            return -1;

        // 무한
        if (!data.get().contains("Options.Balance")) return -1;

        double shopBal;

        try
        {
            shopBal = Double.parseDouble(data.get().getString("Options.Balance")); // 파싱에 실패하면 캐치로 가는 방식.
        }
        // 연동형
        catch (Exception ee)
        {
            String linkedShop = data.get().getString("Options.Balance");

            // 그런 상점이 없음.
            CustomConfig linkedShopData = shopConfigFiles.get(linkedShop);
            if (linkedShopData == null)
            {
                data.get().set("Options.Balance", null);
                data.save();
                DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + shopName + ", " + linkedShop + "/ target shop not found");
                DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + shopName + "/ balance has been reset");
                return -1;
            }

            // 연결 대상이 실제 계좌가 아님.
            try
            {
                if (linkedShopData.get().contains("Options.Balance"))
                {
                    double temp = Double.parseDouble(linkedShopData.get().getString( "Options.Balance"));
                } else
                {
                    return -1;
                }
            } catch (Exception e)
            {
                DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX +
                        shopName + ", " + linkedShop + "/ " +
                        t(null, "ERR.SHOP_LINK_TARGET_ERR"));

                data.get().set("Options.Balance", null);
                data.save();

                DynamicShop.console.sendMessage(Constants.DYNAMIC_SHOP_PREFIX + shopName + "/ balance has been reset");
                return -1;
            }

            shopBal = linkedShopData.get().getDouble("Options.Balance");
        }

        return shopBal;
    }

    // 상점의 잔액 수정
    public void addShopBalance(String shopName, double amount)
    {
        CustomConfig data = shopConfigFiles.get(shopName);
        if (data == null)
            return;

        double old = getShopBalance(shopName);
        if (old < 0) return;

        double newValue = old + amount;
        newValue = (Math.round(newValue * 10000) / 10000.0);

        try
        {
            Double temp = Double.parseDouble(data.get().getString("Options.Balance"));
            data.get().set("Options.Balance", newValue);
        }
        // 연동형
        catch (Exception ee)
        {
            String linkedShop = data.get().getString("Options.Balance");
            CustomConfig linkedShopData = shopConfigFiles.get(linkedShop);
            if (linkedShopData != null)
                linkedShopData.get().set("Options.Balance", newValue);
        }
    }

    public String GetCurrency(@NonNull CustomConfig config)
    {
        return GetCurrency(config.get());
    }
    public String GetCurrency(@NonNull FileConfiguration fileConfiguration)
    {
        String temp = fileConfiguration.getString("Options.currency", "");

        if (temp.equalsIgnoreCase(Constants.S_EXP))
        {
            return Constants.S_EXP;
        }
        else if (temp.equalsIgnoreCase(Constants.S_PLAYERPOINT))
        {
            return Constants.S_PLAYERPOINT;
        }
        else if (temp.equalsIgnoreCase(Constants.S_JOBPOINT))
        {
            return Constants.S_JOBPOINT;
        }
        else
        {
            return Constants.S_VAULT;
        }
    }

    public void SetShopSellCommand(String shopName, int idx, String command)
    {
        CustomConfig shopData = getShopConfig(shopName);
        if (shopData == null)
            return;

        ConfigurationSection shopConf = shopData.get().getConfigurationSection("Options");
        if (shopConf == null)
            return;

        shopConf.set("command.sell." + idx, command);
        CleanupCommandIndex(shopName, "sell");

        shopData.save();
    }

    public void SetShopBuyCommand(String shopName, int idx, String command)
    {
        CustomConfig shopData = getShopConfig(shopName);
        if (shopData == null)
            return;

        ConfigurationSection shopConf = shopData.get().getConfigurationSection("Options");
        if (shopConf == null)
            return;

        shopConf.set("command.buy." + idx, command);
        CleanupCommandIndex(shopName, "buy");

        shopData.save();
    }

    public void CleanupCommandIndex(String shopName, String sellBuyString)
    {
        CustomConfig shopData = getShopConfig(shopName);
        if (shopData == null)
            return;

        ArrayList<Object> tempDatas = new ArrayList<>();
        if (shopData.get().getConfigurationSection("Options.command." + sellBuyString) != null)
        {
            for (Map.Entry<String, Object> s : shopData.get().getConfigurationSection("Options.command." + sellBuyString).getValues(false).entrySet())
            {
                tempDatas.add(s.getValue());
            }
        }
        shopData.get().set("Options.command." + sellBuyString, null);
        for (int i = 0; i < tempDatas.size(); i++)
        {
            shopData.get().set("Options.command." + sellBuyString + "." + i, tempDatas.get(i));
        }
    }
}
