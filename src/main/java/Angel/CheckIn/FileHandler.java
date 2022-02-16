package Angel.CheckIn;

import Angel.FileDatabases;
import Angel.ZoneIDInstanceCreator;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;

class FileHandler implements FileDatabases {
    private final Gson gson;
    private CheckInCore ciCore;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final File jsonCheckInDataFile = new File("data/checkIndata.json");
    private final File jsonCheckInDataTempFile = new File("data/checkInTemp.json");

    FileHandler() {
        gson = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator()).create();
    }

    void setCiCore(CheckInCore ciCore) {
        this.ciCore = ciCore;
    }

    public JsonObject getConfig() throws IOException {
        JsonElement element = JsonParser.parseReader(new FileReader("configs/checkinconfig.json"));
        log.info("Check-In Configuration was read");
        return element.getAsJsonObject();
    }

    public void getDatabase() throws IOException {
        FileReader fileReader = new FileReader(jsonCheckInDataFile);
        JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
        ciCore.setRecords(gson.fromJson(database.get("ciRecords").getAsString(), new TypeToken<List<CheckInRecord>>(){}.getType()));
        ciCore.setResults(gson.fromJson(database.get("ciResults").getAsString(), new TypeToken<List<CheckInResult>>(){}.getType()));
        fileReader.close();
    }

    public void saveDatabase() throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonCheckInDataTempFile)));
        jsonWriter.beginObject()
                .name("ciRecords").value(gson.toJson(ciCore.getRecords()))
                .name("ciResults").value(gson.toJson(ciCore.getResults()))
                .endObject().close();
        log.info("JSONWriter Successfully Ran to Check In Database Temp File");
        while (true) {
            try {
                File backupFile = new File("db-backups/CheckIn/checkInData - " +
                        Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");
                Files.move(Paths.get(jsonCheckInDataFile.getAbsolutePath()), Paths.get(backupFile.getAbsolutePath()));
                break;
            }
            catch (FileSystemException ex) {
                // Take No Action
            }
        }
        // Rename the file
        if (jsonCheckInDataTempFile.renameTo(jsonCheckInDataFile)) {
            log.info("Successfully Renamed Temp Check In File to Original File");
        }
        else {
            log.error("Could Not Rename Check In Temp File");
        }
    }
}