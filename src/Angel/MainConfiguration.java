package Angel;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

public abstract class MainConfiguration {
    JsonObject configObj;
    public Guild guild;
    public String systemPath;
    String token;
    public boolean testModeEnabled;
    public String fieldHeader;
    public Member owner;
    String ownerDiscordID;
    String adminRoleID;
    String staffRoleID;
    String teamRoleID;
    String teamChannelID;
    String helpChannelID;
    String botSpamChannelID;
    String managementChannelID;
    String logChannelID;
    public TextChannel discussionChannel;
    public TextChannel helpChannel;
    public TextChannel botSpamChannel;
    public TextChannel managementChannel;
    public TextChannel logChannel;
    public Role adminRole;
    public Role staffRole;
    public Role teamRole;

    String checkIconURL;
    String warningIconURL;
    String errorIconURL;
    String infoIconURL;
    String stopIconURL;
    String helpIconURL;

    public MainConfiguration(JsonObject importConfigObj) {
        configObj = importConfigObj;
        systemPath = configObj.get("systemPath").getAsString();
        token = configObj.get("token").getAsString();
        testModeEnabled = configObj.get("testModeEnabled").getAsBoolean();
    }
    public void initialSetup() {
        ownerDiscordID = configObj.get("ownerDiscordID").getAsString();
        adminRoleID = configObj.get("adminRole").getAsString();
        staffRoleID = configObj.get("staffRole").getAsString();
        teamRoleID = configObj.get("teamRole").getAsString();
        teamChannelID = configObj.get("teamDiscussionChannel").getAsString();
        helpChannelID = configObj.get("helpChannel").getAsString();
        managementChannelID = configObj.get("managementChannel").getAsString();
        botSpamChannelID = configObj.get("botSpamChannel").getAsString();
        logChannelID = configObj.get("logChannel").getAsString();
        fieldHeader = configObj.get("fieldHeader").getAsString();

        checkIconURL = configObj.get("checkIconURL").getAsString();
        warningIconURL = configObj.get("warningIconURL").getAsString();
        errorIconURL = configObj.get("errorIconURL").getAsString();
        infoIconURL = configObj.get("infoIconURL").getAsString();
        stopIconURL = configObj.get("stopIconURL").getAsString();
        helpIconURL = configObj.get("helpIconURL").getAsString();
    }
    public void discordSetup() {
        owner = guild.getMemberById(ownerDiscordID);

        discussionChannel = guild.getTextChannelById(teamChannelID);
        helpChannel = guild.getTextChannelById(helpChannelID);
        managementChannel = guild.getTextChannelById(managementChannelID);
        botSpamChannel = guild.getTextChannelById(botSpamChannelID);
        logChannel = guild.getTextChannelById(logChannelID);

        adminRole = guild.getRoleById(adminRoleID);
        staffRole = guild.getRoleById(staffRoleID);
        teamRole = guild.getRoleById(teamRoleID);
    }
    public boolean discordGuildConfigurationsExist() {
        return guild.getMembers().contains(guild.getMemberById(ownerDiscordID))
                && guild.getTextChannels().contains(guild.getTextChannelById(teamChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(logChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(helpChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(managementChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(botSpamChannelID))
                && guild.getRoles().contains(guild.getRoleById(adminRoleID))
                && guild.getRoles().contains(guild.getRoleById(staffRoleID))
                && guild.getRoles().contains(guild.getRoleById(teamRoleID));
    }
}