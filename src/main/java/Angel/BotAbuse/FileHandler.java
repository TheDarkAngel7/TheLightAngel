package Angel.BotAbuse;

import Angel.FileGarbageTruck;
import Angel.ZoneIDInstanceCreator;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

class FileHandler {
    Gson gson;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final FileGarbageTruck garbageTruck = new FileGarbageTruck("Bot Abuse", "db-backups/BotAbuse", 9);
    private File jsonBADataFile = new File("data/BAdata.json");
    private File jsonTempBADataFile = new File("data/BAdatatemp.json");
    private Type recordsType = new TypeToken<List<BotAbuseRecord>>(){}.getType();
    private Type dictionary = new TypeToken<Hashtable<String, String>>(){}.getType();

    FileHandler() {

        GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator());
        gson = gsonBuilder.create();
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
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(jsonBADataFile);
            JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
            fileReader.close();
            return gson.fromJson(database.get("records").getAsString(), recordsType);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Dictionary<String, String> getReasonsDictionary() {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(jsonBADataFile);
            JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
            return gson.fromJson(database.get("ReasonsDictionary").getAsString(), dictionary);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveDatabase(List<BotAbuseRecord> records, Dictionary<String, String> reasonsDictionary) {
        try {
            JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempBADataFile)));
            jsonWriter.beginObject();
            jsonWriter.name("records").value(gson.toJson(records));
            jsonWriter.name("ReasonsDictionary").value(gson.toJson(reasonsDictionary));
            jsonWriter.endObject();
            jsonWriter.close();
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