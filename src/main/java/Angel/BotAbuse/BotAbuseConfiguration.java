package Angel.BotAbuse;

import Angel.MainConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;

public abstract class BotAbuseConfiguration {
    private JsonObject configObj;
    BotAbuseMain baMain;
    FileHandler fileHandler;
    MainConfiguration mainConfig;
    String botAbuseRoleID;
    Guild guild;
    Role botAbuseRole;
    int roleScannerInterval;
    int maxDaysAllowedForUndo;
    int hotOffenseMonths;
    int hotOffenseWarning;
    boolean autoPermanent;
    ArrayList<Integer> botAbuseTimes;

    BotAbuseConfiguration(JsonObject configObj, BotAbuseMain baMain, FileHandler fileHandler, MainConfiguration mainConfig) {
        this.configObj = configObj;
        this.baMain = baMain;
        this.fileHandler = fileHandler;
        this.mainConfig = mainConfig;
    }

    // Initial setup contains all of the configuration fields that need to be read.
    // Token is one of them except it cannot be among the configurations to be reloaded,
    // which is why the token is in the constructor
    void initialSetup() {
        // These are configuration settings that can be set without a guild object
        botAbuseRoleID = configObj.get("botAbuseRoleID").getAsString();
        roleScannerInterval = configObj.get("roleScannerIntervalMinutes").getAsInt();
        hotOffenseMonths = configObj.get("oldOffensesConsideredHotInMonths").getAsInt();
        botAbuseTimes = fileHandler.gson.fromJson(configObj.get("botAbuseTimingsInDays").getAsString(), new TypeToken<ArrayList<Integer>>(){}.getType());
        maxDaysAllowedForUndo = configObj.get("maxDaysUndoIsAllowed").getAsInt();
        hotOffenseWarning = configObj.get("warnOnHotOffenseNumber").getAsInt();
        autoPermanent = configObj.get("autoPermanent").getAsBoolean();
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
    public abstract void setConfig(String key, int value);
    public abstract void setConfig(String key, boolean value);
    public abstract boolean setNewBotAbuseRole(long newRoleID);
    public abstract void setNewBotAbuseRole(Role newRole);
    public abstract String addExpiryTime(int newTime);
    public abstract String removeExpiryTime(int removeThisTime, boolean fromInvalidTime);
    public abstract boolean isValidConfig(String key);
}