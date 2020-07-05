package Angel;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.util.EnumSet;

abstract class BotConfiguration {
    JsonObject configObj;
    Guild guild;
    Member owner;
    String ownerDiscordID;
    String token;
    String adminRoleID;
    String staffRoleID;
    String teamRoleID;
    String botAbuseRoleID;
    String teamChannelID;
    String helpChannelID;
    String botSpamChannelID;
    String managementChannelID;
    String logChannelID;
    String fieldHeader;
    TextChannel discussionChannel;
    TextChannel helpChannel;
    TextChannel botSpamChannel;
    TextChannel managementChannel;
    TextChannel logChannel;
    Role adminRole;
    Role staffRole;
    Role teamRole;
    Role botAbuseRole;
    int roleScannerInterval;
    int pingCoolDown;

    BotConfiguration(JsonObject importConfigObj) {
        configObj = importConfigObj;
        token = configObj.get("token").getAsString();
    }
    // Initial setup contains all of the configuration fields that need to be read.
    // Token is one of them except it cannot be among the configurations to be reloaded,
    // which is why the token is in the constructor
    void initialSetup() {
        // These are configuration settings that can be set without a guild object
        ownerDiscordID = configObj.get("ownerDiscordID").getAsString();
        adminRoleID = configObj.get("adminRoleID").getAsString();
        staffRoleID = configObj.get("staffRoleID").getAsString();
        teamRoleID = configObj.get("teamRoleID").getAsString();
        botAbuseRoleID = configObj.get("botAbuseRoleID").getAsString();
        teamChannelID = configObj.get("teamDiscussionChannel").getAsString();
        helpChannelID = configObj.get("helpChannel").getAsString();
        managementChannelID = configObj.get("managementChannel").getAsString();
        botSpamChannelID = configObj.get("botSpamChannel").getAsString();
        logChannelID = configObj.get("logChannel").getAsString();
        fieldHeader = configObj.get("fieldHeader").getAsString();
        roleScannerInterval = configObj.get("roleScannerIntervalMinutes").getAsInt();
        pingCoolDown = configObj.get("pingCoolDownMinutes").getAsInt();
    }
    void discordSetup() {
        // These are configuration settings that have to be set with a guild object
        owner = guild.getMemberById(ownerDiscordID);

        discussionChannel = guild.getTextChannelById(teamChannelID);
        helpChannel = guild.getTextChannelById(helpChannelID);
        managementChannel = guild.getTextChannelById(managementChannelID);
        botSpamChannel = guild.getTextChannelById(botSpamChannelID);
        logChannel = guild.getTextChannelById(logChannelID);

        adminRole = guild.getRoleById(adminRoleID);
        staffRole = guild.getRoleById(staffRoleID);
        teamRole = guild.getRoleById(teamRoleID);
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
        return guild.getMembers().contains(guild.getMemberById(ownerDiscordID))
                && guild.getTextChannels().contains(guild.getTextChannelById(teamChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(logChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(helpChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(managementChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(botSpamChannelID))
                && guild.getRoles().contains(guild.getRoleById(botAbuseRoleID))
                && guild.getRoles().contains(guild.getRoleById(adminRoleID))
                && guild.getRoles().contains(guild.getRoleById(staffRoleID))
                && guild.getRoles().contains(guild.getRoleById(teamRoleID));
    }
}
abstract class CoreConfiguration {
    JsonObject configObj;
    String systemPath;
    String host;
    boolean testModeEnabled;
    int maxDaysAllowedForUndo;

    CoreConfiguration(JsonObject importConfigObj) {
        configObj = importConfigObj;
        systemPath = configObj.get("systemPath").getAsString();
        host = configObj.get("host").getAsString();
    }
    void setup() {
        testModeEnabled = configObj.get("testModeEnabled").getAsBoolean();
        maxDaysAllowedForUndo = configObj.get("maxDaysUndoIsAllowed").getAsInt();
    }

    void reload(JsonObject importNewConfigObj) {
        configObj = importNewConfigObj;
        setup();
    }
}
