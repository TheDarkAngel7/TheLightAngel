package Angel.Nicknames;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;

class FileHandler {
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private NickCore nickCore;
    Gson gson = new Gson();
    private File jsonNickDataFile = new File("data/nickdata.json");
    private File jsonTempNickDataFile = new File("data/nicktempdata.json");
    private Type longType = new TypeToken<ArrayList<Long>>(){}.getType();
    private Type integerType = new TypeToken<ArrayList<Integer>>(){}.getType();
    private Type stringType = new TypeToken<ArrayList<String>>(){}.getType();
    private Type oldNickDictionaryType = new TypeToken<Hashtable<Long, ArrayList<String>>>(){}.getType();
    FileReader fileReader;

    FileHandler(NickCore nickCore) {
        this.nickCore = nickCore;
    }

    JsonObject getConfig() throws IOException {
        fileReader = new FileReader("configs/nickconfig.json");
        JsonElement element = JsonParser.parseReader(fileReader);
        fileReader.close();
        return element.getAsJsonObject();
    }

    void getDatabase() throws IOException {
        // This is to ensure the fileReader closes at the end of this method
        fileReader = new FileReader(jsonNickDataFile);
        JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
        nickCore.requestID = gson.fromJson(database.get("RequestID").getAsString(), integerType);
        nickCore.discordID = gson.fromJson(database.get("DiscordID").getAsString(), longType);
        nickCore.oldNickname = gson.fromJson(database.get("OldNickname").getAsString(), stringType);
        nickCore.newNickname = gson.fromJson(database.get("NewNickname").getAsString(), stringType);
        nickCore.oldNickDictionary = gson.fromJson(database.get("OldNameDictionary").getAsString(), oldNickDictionaryType);
        log.info("Nickname Database Successfully Setup");
        fileReader.close();
    }
    void saveDatabase() throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempNickDataFile)));
        jsonWriter.beginObject();
        jsonWriter.name("RequestID").value(gson.toJson(nickCore.requestID));
        jsonWriter.name("DiscordID").value(gson.toJson(nickCore.discordID));
        jsonWriter.name("OldNickname").value(gson.toJson(nickCore.oldNickname));
        jsonWriter.name("NewNickname").value(gson.toJson(nickCore.newNickname));
        jsonWriter.name("OldNameDictionary").value(gson.toJson(nickCore.oldNickDictionary));
        jsonWriter.endObject();
        jsonWriter.close();
        log.info("JSONWriter Successfully Ran to Nickname Database Temp File");
        File backupFile = new File("db-backups/Nicknames/Nickdata - " +
                Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");
        Files.move(Paths.get(jsonNickDataFile.getAbsolutePath()), Paths.get(backupFile.getAbsolutePath()));
        // Rename the file
        if (jsonTempNickDataFile.renameTo(jsonNickDataFile)) {
            log.info("Successfully Renamed Nickname Temp File to Original File");
        }
        else {
            log.error("Could Not Rename Nickname Temp File");
        }
    }
}
