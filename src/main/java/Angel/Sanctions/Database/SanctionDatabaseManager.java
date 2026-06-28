package Angel.Sanctions.Database;

import Angel.FileGarbageTruck;
import Angel.RuntimeTypeAdapterFactory;
import Angel.Sanctions.SanctionLogic;
import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class SanctionDatabaseManager extends Timer implements SanctionLogic {
    private final Logger log = LogManager.getLogger(SanctionDatabaseManager.class);
    private final Path databasePath = Paths.get(".", "data", "sanctiondata.json");
    private final Gson gson;
    private SanctionDatabaseContainer container;
    private final FileGarbageTruck fileGarbageTruck = new FileGarbageTruck("Sanctions", "db-backups/Sanctions", 15);

    public SanctionDatabaseManager() {
        RuntimeTypeAdapterFactory<SanctionInfo> sanctionAdapterFactory =
                RuntimeTypeAdapterFactory.of(SanctionInfo.class, "type")
                        .registerSubtype(SuspensionInfo.class, "Suspension")
                        .registerSubtype(BanInfo.class, "Ban");

        this.gson = new GsonBuilder()
                .registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .registerTypeAdapterFactory(sanctionAdapterFactory)
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
    }

    public SanctionDatabaseContainer loadDatabase() {
        try (FileReader reader = new FileReader(databasePath.toFile())) {
            SanctionDatabaseContainer container = gson.fromJson(reader, SanctionDatabaseContainer.class);
            this.container = container != null ? container : new SanctionDatabaseContainer();
            log.info("sanctiondata.json file has been loaded");
            return this.container;
        }
        catch (IOException e) {
            log.warn("Unable to read sanctiondata.json: {}", e.getMessage(), e);
            this.container = new SanctionDatabaseContainer();
            return container;
        }
        finally {
            startExpiryTimer();
        }
    }

    public void saveDatabase() {

        while (true) {
            try {
                File backupFile = new File("db-backups/Sanctions/sanctiondata - " +
                        Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");

                Files.move(databasePath, Paths.get(backupFile.getAbsolutePath()));
                break;
            }
            catch (NoSuchFileException e) {
                log.warn("sanctiondata.json does not exist");
                break;
            }
            catch (IOException e) {
                log.error("Unable to save backup sanctiondata file: {}", e.getMessage(), e);
            }
        }

        fileGarbageTruck.dumpFiles();

        try (FileWriter writer = new FileWriter(databasePath.toFile())) {
            gson.toJson(container, writer);
            log.info("sanctiondata.json file successfully saved");
        }
        catch (IOException e) {
            log.error("Unable to save sanctiondata.json: {}", e.getMessage(), e);
        }
    }

    private void startExpiryTimer() {
        log.info("Sanction Expiry Timer is Starting...");
        this.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (SanctionInfo record : container.getSanctionList()) {
                    if (ZonedDateTime.now().isAfter(record.getExpiryDate()) && record.sanctionApplied()) {
                        record.reverseSanction();
                        container.removeSanction(record.getDiscordID());
                        saveDatabase();
                    }
                    else if (ZonedDateTime.now().isBefore(record.getExpiryDate()) && !record.sanctionApplied()) {
                        record.reapplySanction();
                    }
                }
            }
        }, 0, 300000);
    }
}
