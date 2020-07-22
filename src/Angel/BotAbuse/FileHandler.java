package Angel.BotAbuse;

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
import java.util.Date;
import java.util.Hashtable;

class FileHandler {
    Gson gson = new Gson();
    private BotAbuseCore baCore;
    private final Logger log = LogManager.getLogger(Angel.FileHandler.class);
    private File jsonBADataFile = new File("data/BAdata.json");
    private File jsonTempBADataFile = new File("data/BAdatatemp.json");
    private Type longType = new TypeToken<ArrayList<Long>>(){}.getType();
    private Type integerType = new TypeToken<ArrayList<Integer>>(){}.getType();
    private Type stringType = new TypeToken<ArrayList<String>>(){}.getType();
    private Type dateType = new TypeToken<ArrayList<Date>>(){}.getType();
    private Type dictionary = new TypeToken<Hashtable<String, String>>(){}.getType();

    FileHandler(BotAbuseCore baCore) {
        this.baCore = baCore;
    }

    JsonObject getConfig() throws IOException {
        FileReader fileReader = new FileReader("configs/botabuseconfig.json");
        JsonElement element = JsonParser.parseReader(fileReader);
        fileReader.close();
        return element.getAsJsonObject();
    }

    void getDatabase() throws IllegalStateException, IOException {
        // This is to ensure the fileReader closes at the end of this method
        FileReader fileReader = new FileReader(jsonBADataFile);
        JsonObject database = JsonParser.parseReader(fileReader).getAsJsonObject();
        baCore.discordID = gson.fromJson(database.get("DiscordID").getAsString(), longType);
        baCore.issuingTeamMember = gson.fromJson(database.get("TeamMembers").getAsString(), stringType);
        baCore.repOffenses = gson.fromJson(database.get("RepOffenses").getAsString(), integerType);
        baCore.issuedDates = gson.fromJson(database.get("DatesIssued").getAsString(), dateType);
        baCore.expiryDates = gson.fromJson(database.get("ExpiryDates").getAsString(), dateType);
        baCore.reasons = gson.fromJson(database.get("Reasons").getAsString(), stringType);
        baCore.proofImages = gson.fromJson(database.get("ProofImages").getAsString(), stringType);
        baCore.currentBotAbusers = gson.fromJson(database.get("CurrentBotAbusers").getAsString(), longType);
        baCore.reasonsDictionary = gson.fromJson(database.get("ReasonsDictionary").getAsString(), dictionary);
        log.info("Bot Abuse Database Successfully Setup");
        fileReader.close();
    }

    void saveDatabase() throws IOException {
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempBADataFile)));
        jsonWriter.beginObject();
        jsonWriter.name("DiscordID").value(gson.toJson(baCore.discordID));
        jsonWriter.name("TeamMembers").value(gson.toJson(baCore.issuingTeamMember));
        jsonWriter.name("RepOffenses").value(gson.toJson(baCore.repOffenses));
        jsonWriter.name("DatesIssued").value(gson.toJson(baCore.issuedDates));
        jsonWriter.name("ExpiryDates").value(gson.toJson(baCore.expiryDates));
        jsonWriter.name("Reasons").value(gson.toJson(baCore.reasons));
        jsonWriter.name("ProofImages").value(gson.toJson(baCore.proofImages));
        jsonWriter.name("CurrentBotAbusers").value(gson.toJson(baCore.currentBotAbusers));
        jsonWriter.name("ReasonsDictionary").value(gson.toJson(baCore.reasonsDictionary));
        jsonWriter.endObject();
        jsonWriter.close();
        log.info("JSONWriter Successfully Ran to Bot Abuse Database Temp File");
        File backupFile = new File("db-backups/BotAbuse/BAdata - " +
                Calendar.getInstance().getTime().toString().replace(':', ' ') + ".json");
        Files.move(Paths.get(jsonBADataFile.getAbsolutePath()), Paths.get(backupFile.getAbsolutePath()));
        // Rename the file
        if (jsonTempBADataFile.renameTo(jsonBADataFile)) {
            log.info("Successfully Renamed Temp Bot Abuse File to Original File");
        }
        else {
            log.error("Could Not Rename Bot Abuse Temp File");
        }
    }
}
