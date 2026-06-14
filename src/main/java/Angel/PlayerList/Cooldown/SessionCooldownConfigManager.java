package Angel.PlayerList.Cooldown;

import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SessionCooldownConfigManager {
    private final Logger log = LogManager.getLogger(SessionCooldownConfigManager.class);
    private final Path configPath = Paths.get(".", "configs", "SessionCooldownConfig.json");
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();
    private SessionCooldownConfigContainer container;

    public SessionCooldownConfigContainer loadConfiguration() {
        if (Files.notExists(configPath)) {
            log.warn("SessionCooldownConfig.json file does not exist. Creating new container...");
            this.container = new SessionCooldownConfigContainer();
            return container;
        }

        try (FileReader reader = new FileReader(configPath.toFile())) {
            SessionCooldownConfigContainer container = gson.fromJson(reader, SessionCooldownConfigContainer.class);
            this.container = container != null ? container : new SessionCooldownConfigContainer();
            return this.container;
        }
        catch (IOException e) {
            log.error("Unable to read SessionCooldownConfig.json: {}", e.getMessage(), e);
            this.container = new SessionCooldownConfigContainer();
            return container;
        }
    }

    public void saveConfiguration() {
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            gson.toJson(container, writer);
            log.info("SessionCooldownConfig.json file has been saved");
        }
        catch (IOException e) {
            log.error("Unable to save SessionCooldownConfig.json: {}", e.getMessage(), e);
        }
    }
}
