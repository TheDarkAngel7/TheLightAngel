package Angel.BotAbuse;

import Angel.FileDatabases;
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
import java.util.Hashtable;
import java.util.List;

class FileHandler implements FileDatabases {
    Gson gson;
    private BotAbuseCore baCore;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private File jsonBADataFile = new File("data/BAdata.json");
    private File jsonTempBADataFile = new File("data/BAdatatemp.json");
    private Type recordsType = new TypeToken<List<BotAbuseRecord>>(){}.getType();
    private Type dictionary = new TypeToken<Hashtable<String, String>>(){}.getType();

    FileHandler(BotAbuseCore baCore) {
        this.baCore = baCore;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator());
        gson = gsonBuilder.create();
    }

    public JsonObject getConfig() throws IOException {
        FileReader fileReader = new FileReader("configs/botabuseconfig.json");
        JsonElement element = JsonParser.parseReader(fileReader);
        fileReader.close();
        return element.getAsJsonObject();
    }

    public void getDatabase() throws IOException {
        // This is to ensure the fileReader closes at the end of this method
        FileReader fileReader = new FileReader(jsonBADataFile);
        JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
        baCore.setRecords(gson.fromJson(database.get("records").getAsString(), recordsType));
        baCore.reasonsDictionary = gson.fromJson(database.get("ReasonsDictionary").getAsString(), dictionary);
        log.info("Bot Abuse Database Successfully Setup");
        fileReader.close();
    }

    public void saveDatabase() throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempBADataFile)));
        jsonWriter.beginObject();
        jsonWriter.name("records").value(gson.toJson(baCore.getRecords()));
        jsonWriter.name("ReasonsDictionary").value(gson.toJson(baCore.reasonsDictionary));
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
        // Rename the file
        if (jsonTempBADataFile.renameTo(jsonBADataFile)) {
            log.info("Successfully Renamed Temp Bot Abuse File to Original File");
        }
        else {
            log.error("Could Not Rename Bot Abuse Temp File");
        }
    }
}