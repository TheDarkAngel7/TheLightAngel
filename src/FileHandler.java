import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;
import java.lang.reflect.Type;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;

class FileHandler {
    Gson gson = new Gson();
    File jsonDataFile = new File("data/data.json");
    File jsonTempDataFile = new File("data/datatemp.json");
    private JsonElement database = JsonParser.parseReader(new FileReader(jsonDataFile));
    private Type longType = new TypeToken<ArrayList<Long>>(){}.getType();
    private Type integerType = new TypeToken<ArrayList<Integer>>(){}.getType();
    private Type stringType = new TypeToken<ArrayList<String>>(){}.getType();
    private Type dateType = new TypeToken<ArrayList<Date>>(){}.getType();

    FileHandler() throws IOException {
    }

    JsonObject getConfig() throws FileNotFoundException {
        JsonElement element = JsonParser.parseReader(new FileReader("config.json"));
        return element.getAsJsonObject();
    }
    ArrayList<Long> getDiscordIDs() {
        return gson.fromJson(database.getAsJsonObject().get("DiscordID").getAsString(), longType);
    }
    ArrayList<String> getTeamMembers() {
        return gson.fromJson(database.getAsJsonObject().get("TeamMembers").getAsString(), stringType);
    }
    ArrayList<Integer> getRepOffenses() {
        return gson.fromJson(database.getAsJsonObject().get("RepOffenses").getAsString(), integerType);
    }
    ArrayList<Date> getIssuedDates() {
        return gson.fromJson(database.getAsJsonObject().get("DatesIssued").getAsString(), dateType);
    }
    ArrayList<Date> getExpiryDates() {
        return gson.fromJson(database.getAsJsonObject().get("ExpiryDates").getAsString(), dateType);
    }
    ArrayList<String> getReasons() {
        return gson.fromJson(database.getAsJsonObject().get("Reasons").getAsString(), stringType);
    }
    ArrayList<String> getProofImages() {
        return gson.fromJson(database.getAsJsonObject().get("ProofImages").getAsString(), stringType);
    }
    ArrayList<Long> getCurrentBotAbusers() {
        return gson.fromJson(database.getAsJsonObject().get("CurrentBotAbusers").getAsString(), longType);
    }

    void writeArrayData(ArrayList<Long> discordID, ArrayList<String> teamMembers, ArrayList<Integer> repOffenses, ArrayList<Date> issuedDates,
                       ArrayList<Date> expiryDates, ArrayList<String> reasons, ArrayList<String> proofImages,
                       ArrayList<Long> currentBotAbusers) throws IOException {
        String jsonDiscordID = gson.toJson(discordID);
        String jsonTeamMembers = gson.toJson(teamMembers);
        String jsonRepOffenses = gson.toJson(repOffenses);
        String jsonDatesIssued = gson.toJson(issuedDates);
        String jsonDatesToExpire = gson.toJson(expiryDates);
        String jsonReasons = gson.toJson(reasons);
        String jsonProofImages = gson.toJson(proofImages);
        String jsonCurrentBotAbusers = gson.toJson(currentBotAbusers);
        System.out.println(jsonDiscordID + "\n" + jsonTeamMembers + "\n" + jsonRepOffenses + "\n" + jsonDatesIssued
        + "\n" + jsonDatesToExpire + "\n" + jsonReasons + "\n" + jsonProofImages + "\n" + jsonCurrentBotAbusers);
        JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonTempDataFile)));
        jsonWriter.beginObject();
        jsonWriter.name("DiscordID").value(jsonDiscordID);
        jsonWriter.name("TeamMembers").value(jsonTeamMembers);
        jsonWriter.name("RepOffenses").value(jsonRepOffenses);
        jsonWriter.name("DatesIssued").value(jsonDatesIssued);
        jsonWriter.name("ExpiryDates").value(jsonDatesToExpire);
        jsonWriter.name("Reasons").value(jsonReasons);
        jsonWriter.name("ProofImages").value(jsonProofImages);
        jsonWriter.name("CurrentBotAbusers").value(jsonCurrentBotAbusers);
        jsonWriter.endObject();
        jsonWriter.close();
        if (jsonDataFile.delete()) {
            System.out.println("[System] Successfully Deleted Original File");
        }
        else {
            while (jsonDataFile.exists()) {
                System.out.println("Result of Deletion: " + jsonDataFile.delete());
            }
        }

        // Rename the file
        if (jsonTempDataFile.renameTo(jsonDataFile)) {
            System.out.println("[System] Successfully Renamed Temp File to Original File");
        }
        else {
            System.out.println("[System] Couldn't Rename Temp File");
        }
    }
}