package Angel.Nicknames;

import Angel.FileGarbageTruck;
import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

class FileHandler {
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private final Gson gson;
    private final File jsonNickDataFile = new File("data/nickdata.json");
    private final File jsonTempNickDataFile = new File("data/nicktempdata.json");
    private final Type longType = new TypeToken<ArrayList<Long>>(){}.getType();
    private final Type integerType = new TypeToken<ArrayList<Integer>>(){}.getType();
    private final Type stringType = new TypeToken<ArrayList<String>>(){}.getType();
    private FileGarbageTruck garbageTruck = new FileGarbageTruck("Nicknames", "db-backups/Nicknames", 11);
    private NicknameFile nicknameFile;

    FileHandler() {
        GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter());
        gson = gsonBuilder.create();

        try (FileReader fileReader = new FileReader(jsonNickDataFile)) {
            nicknameFile = gson.fromJson(fileReader, NicknameFile.class);
        }
        catch (FileNotFoundException e) {
            log.fatal("Nickname FileHandler Constructor: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonObject getConfig() {
        try {
            FileReader fileReader = new FileReader("configs/nickconfig.json");
            JsonElement element = JsonParser.parseReader(fileReader);
            fileReader.close();
            return element.getAsJsonObject();
        }
        catch (IOException e) {
            log.fatal("Nickname Configuration Read: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    List<NicknameRequest> getNameRequests() {
        return nicknameFile.getNicknameRequests();
    }

    List<PlayerNameHistory> getNameHistory() {
        return nicknameFile.getPlayerNameHistory();
    }
    public void saveDatabase(List<NicknameRequest> requests, List<PlayerNameHistory> oldNickDictionary) {
        try {
            FileWriter fileWriter = new FileWriter(jsonTempNickDataFile);

            fileWriter.write(gson.toJson(new NicknameFile(requests, oldNickDictionary)));

            fileWriter.close();

            log.info("JSONWriter Successfully Ran to Nickname Database Temp File");
            while (true) {
                try {
                    File backupFile = new File("db-backups/Nicknames/Nickdata - " +
                            Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");
                    Files.move(Paths.get(jsonNickDataFile.getAbsolutePath()), Paths.get(backupFile.getAbsolutePath()));
                    break;
                }
                catch (FileSystemException ex) {
                    // Take No Action
                }
            }
        }
        catch (IOException e) {
            log.fatal("Unable to Save Nickname Database: " + e.getMessage());
            throw new RuntimeException(e);
        }
        // Rename the file
        if (jsonTempNickDataFile.renameTo(jsonNickDataFile)) {
            log.info("Successfully Renamed Nickname Temp File to Original File");
        }
        else {
            log.error("Could Not Rename Nickname Temp File");
        }

        garbageTruck.dumpFiles();
    }
}