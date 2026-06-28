package Angel.Sanctions.Configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SanctionConfigManager {
    private final Logger log = LogManager.getLogger(SanctionConfigManager.class);
    private final Path configPath = Paths.get(".", "configs", "sanctionConfig.json");
    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private ConfigurationFile config;

    public SanctionConfigManager() {
        loadConfig();
    }

    public void reloadConfig() {
        loadConfig();
        log.info("Successfully reloaded Configuration File!");
    }

    private void loadConfig() {
        if (Files.notExists(configPath)) {
            Exception e = new IllegalStateException("sanctionConfig.json Does Not Exist!");
            log.fatal("sanctionConfig.json Does Not Exist!", e);
            throw new RuntimeException(e);
        }

        try (Reader reader = new FileReader(configPath.toFile())) {
            this.config = gson.fromJson(reader, ConfigurationFile.class);
            log.info("Sanction Configuration Successfully Loaded!");
        }
        catch (IOException e) {
            log.fatal("sanctionConfig.json Load Failed!", e);
            throw new RuntimeException(e);
        }
    }

    public ConfigurationFile getConfig() {
        return config;
    }
}
