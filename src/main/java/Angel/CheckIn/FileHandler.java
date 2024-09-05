package Angel.CheckIn;

import Angel.FileGarbageTruck;
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

class FileHandler {
    private final Gson gson;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final File jsonCheckInDataFile = new File("data/checkIndata.json");
    private final File jsonCheckInDataTempFile = new File("data/checkInTemp.json");
    private final FileGarbageTruck garbageTruck = new FileGarbageTruck("Check-In", "db-backups/CheckIn", 14);

    FileHandler() {
        gson = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator()).create();
    }

    public JsonObject getConfig()  {
        JsonElement element;
        try {
            element = JsonParser.parseReader(new FileReader("configs/checkinconfig.json"));
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        log.info("Check-In Configuration was read");
        return element.getAsJsonObject();
    }

    public List<CheckInResult> getDatabase() {
        try {
            FileReader fileReader = new FileReader(jsonCheckInDataFile);
            JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
            fileReader.close();

            return gson.fromJson(database.get("ciResults").getAsString(), new TypeToken<List<CheckInResult>>(){}.getType());
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void saveDatabase(List<CheckInResult> ciResults) throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(Files.newOutputStream(jsonCheckInDataTempFile.toPath())));
        jsonWriter.beginObject()
                .name("ciResults").value(gson.toJson(ciResults))
                .endObject();
        jsonWriter.close();
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

        garbageTruck.dumpFiles();
    }
}