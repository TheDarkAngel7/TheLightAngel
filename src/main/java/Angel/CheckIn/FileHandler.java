package Angel.CheckIn;

import Angel.FileGarbageTruck;
import Angel.Security.FileEncryptionManager;
import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

class FileHandler {
    private final Gson gson;
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final File jsonCheckInDataFile = new File("data/checkIndata.json");
    private final File jsonCheckInDataTempFile = new File("data/checkInTemp.json");
    private final FileGarbageTruck garbageTruck = new FileGarbageTruck("Check-In", "db-backups/CheckIn", 14);

    FileHandler() {
        gson = new GsonBuilder()
                .registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter()).create();
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
        if (!jsonCheckInDataFile.exists()) {
            log.error("Check-In data file does not exist, returning empty CheckInResult array!");
            return new ArrayList<>();
        }

        String decryptedJson = FileEncryptionManager.readEncryptedFile(jsonCheckInDataFile, "Check-In");

        if (decryptedJson.isEmpty()) {
            log.error("Unable to Read Check-In Data from Encrypted File! Returning Empty Check In Result List");
            return new ArrayList<>();
        }
        return gson.fromJson(decryptedJson, new TypeToken<List<CheckInResult>>(){}.getType());
    }

    public void saveDatabase(List<CheckInResult> ciResults) throws IOException {
        String decryptedJson = gson.toJson(ciResults);

        FileEncryptionManager.writeEncryptedFile(jsonCheckInDataTempFile, "Check-In", decryptedJson);

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