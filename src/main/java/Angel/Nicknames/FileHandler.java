package Angel.Nicknames;

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
    private FileReader fileReader;
    private JsonObject database;

    FileHandler() {
        GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator());
        gson = gsonBuilder.create();

        try {
            database = JsonParser.parseReader(new FileReader(jsonNickDataFile)).getAsJsonObject();
        }
        catch (FileNotFoundException e) {
            log.fatal("Nickname FileHandler Constructor: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public JsonObject getConfig() {
        try {
            fileReader = new FileReader("configs/nickconfig.json");
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
        return gson.fromJson(database.get("NicknameRequests").getAsString(), new TypeToken<ArrayList<NicknameRequest>>(){}.getType());
    }

    List<PlayerNameHistory> getNameHistory() {
        return gson.fromJson(database.get("OldNameDictionary").getAsString(), new TypeToken<ArrayList<PlayerNameHistory>>(){}.getType());
    }
    public void saveDatabase(List<NicknameRequest> requests, List<PlayerNameHistory> oldNickDictionary) {
        try {
            JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempNickDataFile)));
            jsonWriter.beginObject()
                    .name("NicknameRequests").value(gson.toJson(requests))
                    .name("OldNameDictionary").value(gson.toJson(oldNickDictionary))
                    .endObject().close();
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