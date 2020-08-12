package Angel.BotAbuse;

import Angel.MainConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;

abstract class BotConfiguration {
    JsonObject configObj;
    MainConfiguration mainConfig;
    String botAbuseRoleID;
    Guild guild;
    Role botAbuseRole;
    int roleScannerInterval;
    boolean rabbitMQEnabled;

    BotConfiguration(JsonObject importConfigObj, MainConfiguration importMainConfig) {
        configObj = importConfigObj;
        mainConfig = importMainConfig;
    }
    // Initial setup contains all of the configuration fields that need to be read.
    // Token is one of them except it cannot be among the configurations to be reloaded,
    // which is why the token is in the constructor
    void initialSetup() {
        // These are configuration settings that can be set without a guild object
        botAbuseRoleID = configObj.get("botAbuseRoleID").getAsString();
        rabbitMQEnabled = configObj.get("rabbitMQEnabled").getAsBoolean();
        roleScannerInterval = configObj.get("roleScannerIntervalMinutes").getAsInt();
    }
    void discordSetup() {
        // These are configuration settings that have to be set with a guild object
        botAbuseRole = guild.getRoleById(botAbuseRoleID);
    }
    // The reload method accepts an JsonObject as an argument,
    // the new JsonObject was retrieved from the FileHandler.getConfig() method.
    // This is then set as the new configObject and both setup methods run again
    boolean reload(JsonObject importNewConfig) {
        configObj = importNewConfig;
        initialSetup();
        if (configsExist()) {
            discordSetup();
            return true;
        }
        else return false;

    }
    // Separate Method for checking each of the discord guild configurations - We're checking to see if they exist in the server
    boolean configsExist() {
        return guild.getRoles().contains(guild.getRoleById(botAbuseRoleID));
    }
}
abstract class CoreConfiguration {
    JsonObject configObj;
    FileHandler fileHandler;
    String host;
    int maxDaysAllowedForUndo;
    int hotOffenseMonths;
    ArrayList<Integer> botAbuseTimes;

    CoreConfiguration(JsonObject importConfigObj, FileHandler fileHandler) {
        configObj = importConfigObj;
        host = configObj.get("host").getAsString();
        this.fileHandler = fileHandler;
        setup();
    }
    void setup() {
        hotOffenseMonths = configObj.get("oldOffensesConsideredHotInMonths").getAsInt();
        botAbuseTimes = fileHandler.gson.fromJson(configObj.get("botAbuseTimingsInDays").getAsString(), new TypeToken<ArrayList<Integer>>(){}.getType());
        maxDaysAllowedForUndo = configObj.get("maxDaysUndoIsAllowed").getAsInt();
    }

    void reload(JsonObject importNewConfigObj) {
        configObj = importNewConfigObj;
        setup();
    }
}