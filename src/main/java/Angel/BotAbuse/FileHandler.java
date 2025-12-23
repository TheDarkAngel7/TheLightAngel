package Angel.BotAbuse;

import Angel.FileGarbageTruck;
import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

class FileHandler {
    Gson gson;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final FileGarbageTruck garbageTruck = new FileGarbageTruck("Bot Abuse", "db-backups/BotAbuse", 9);
    private BotAbuseFile botAbuseFile;
    private File jsonBADataFile = new File("data/BAdata.json");
    private File jsonTempBADataFile = new File("data/BAdatatemp.json");
    private Type recordsType = new TypeToken<List<BotAbuseRecord>>(){}.getType();
    private Type dictionary = new TypeToken<Hashtable<String, String>>(){}.getType();

    FileHandler() {
        gson = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).create();

        try (FileReader fileReader = new FileReader(jsonBADataFile)) {
            botAbuseFile = gson.fromJson(fileReader, BotAbuseFile.class);
            fileReader.close();
        }
        catch (IOException e) {
            log.fatal(e.getMessage());
        }
    }

    public JsonObject getConfig() {
        try {
            FileReader fileReader = new FileReader("configs/botabuseconfig.json");
            JsonElement element = JsonParser.parseReader(fileReader);
            fileReader.close();
            return element.getAsJsonObject();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<BotAbuseRecord> getRecords() {
        return botAbuseFile.getRecords();
    }

    public Map<String, String> getReasonsDictionary() {
        return botAbuseFile.getReasonsDictionary();
    }

    public void saveDatabase(List<BotAbuseRecord> records, Map<String, String> reasonsDictionary) {
        try {
            FileWriter fileWriter = new FileWriter(jsonTempBADataFile);

            fileWriter.write(gson.toJson(new BotAbuseFile(records, reasonsDictionary)));

            fileWriter.close();

            log.info("JSONWriter Successfully Ran to Bot Abuse Database Temp File");
            while (true) {
                try {
                    File backupFile = new File("db-backups/BotAbuse/BAdata - " +
                            Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");
                    Files.move(Paths.get(jsonBADataFile.getAbsolutePath()), Paths.get(backupFile.getAbsolutePath()));
                    break;
                }
                catch (FileSystemException ex) {
                    // Take No Action
                }
            }
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        // Rename the file
        if (jsonTempBADataFile.renameTo(jsonBADataFile)) {
            log.info("Successfully Renamed Temp Bot Abuse File to Original File");
        }
        else {
            log.error("Could Not Rename Bot Abuse Temp File");
        }

        garbageTruck.dumpFiles();
    }
}