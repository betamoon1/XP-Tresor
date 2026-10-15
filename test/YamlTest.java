import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
public class YamlTest {
    public static void main(String[] a) throws Exception {
        YamlConfiguration en = YamlConfiguration.loadConfiguration(new File("res/lang/messages_en.yml"));
        int keys = en.getKeys(true).size();
        for (File f : new File("res/lang").listFiles()) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(f);
            int missing = 0;
            for (String k : en.getKeys(true)) if (!en.isConfigurationSection(k) && y.getString(k) == null) missing++;
            System.out.println(f.getName() + " keys=" + y.getKeys(true).size() + "/" + keys + " missing=" + missing
                    + " enabled=" + y.getString("words.enabled") + " positive=" + y.getString("words.positive"));
        }
        YamlConfiguration c = new YamlConfiguration();
        c.load(new File("res/config.yml"));
        System.out.println("config language=" + c.getString("language") + " perPlayer=" + c.getBoolean("per-player-language") + " ranks=" + c.getConfigurationSection("limit.ranks").getKeys(false));
    }
}
