package Angel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Type;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;

class FileHandler {
    private Core core;
    Gson gson = new Gson();
    private File jsonDataFile = new File("data/data.json");
    private File jsonTempDataFile = new File("data/datatemp.json");
    private final Logger log = LogManager.getLogger(FileHandler.class);
    private Type longType = new TypeToken<ArrayList<Long>>(){}.getType();
    private Type integerType = new TypeToken<ArrayList<Integer>>(){}.getType();
    private Type stringType = new TypeToken<ArrayList<String>>(){}.getType();
    private Type dateType = new TypeToken<ArrayList<Date>>(){}.getType();

    FileHandler(Core importCore) {
        core = importCore;
    }
    JsonObject getConfig() throws FileNotFoundException {
        JsonElement element = JsonParser.parseReader(new FileReader("config.json"));
        return element.getAsJsonObject();
    }
    void getDatabase() throws IllegalStateException, FileNotFoundException {
        JsonObject database = JsonParser.parseReader(new FileReader(jsonDataFile)).getAsJsonObject();
        core.discordID = gson.fromJson(database.get("DiscordID").getAsString(), longType);
        core.issuingTeamMember = gson.fromJson(database.getAsJsonObject().get("TeamMembers").getAsString(), stringType);
        core.repOffenses = gson.fromJson(database.get("RepOffenses").getAsString(), integerType);
        core.issuedDates = gson.fromJson(database.get("DatesIssued").getAsString(), dateType);
        core.expiryDates = gson.fromJson(database.get("ExpiryDates").getAsString(), dateType);
        core.reasons = gson.fromJson(database.get("Reasons").getAsString(), stringType);
        core.proofImages = gson.fromJson(database.get("ProofImages").getAsString(), stringType);
        core.currentBotAbusers = gson.fromJson(database.get("CurrentBotAbusers").getAsString(), longType);
        log.info("Database Successfully Setup");
    }
    void saveDatabase() throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempDataFile)));
        jsonWriter.beginObject();
        jsonWriter.name("DiscordID").value(gson.toJson(core.discordID));
        jsonWriter.name("TeamMembers").value(gson.toJson(core.issuingTeamMember));
        jsonWriter.name("RepOffenses").value(gson.toJson(core.repOffenses));
        jsonWriter.name("DatesIssued").value(gson.toJson(core.issuedDates));
        jsonWriter.name("ExpiryDates").value(gson.toJson(core.expiryDates));
        jsonWriter.name("Reasons").value(gson.toJson(core.reasons));
        jsonWriter.name("ProofImages").value(gson.toJson(core.proofImages));
        jsonWriter.name("CurrentBotAbusers").value(gson.toJson(core.currentBotAbusers));
        jsonWriter.endObject();
        jsonWriter.close();
        log.info("JSONWriter Successfully Ran to Database Temp File");
        if (jsonDataFile.delete()) {
            log.info("Successfully Deleted Original File");
        }
        else {
            log.warn("Could Not Delete Original Data File - Trying Again Over and Over...");
            while (jsonDataFile.exists()) {
                System.out.println("Result of Deletion: " + jsonDataFile.delete());
            }
            log.info("Deletion Finally Ran");
        }

        // Rename the file
        if (jsonTempDataFile.renameTo(jsonDataFile)) {
            log.info("Successfully Renamed Temp File to Original File");
        }
        else {
            System.out.println("[System] Couldn't Rename Temp File");
            log.error("Could Not Rename Temp File");
        }
    }
}