package Angel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;

public abstract class MainConfiguration {
    private JsonObject configObj;
    Guild guild;
    public String token;
    public boolean testModeEnabled;
    boolean deleteOriginalNonStaffCommands;
    boolean deleteOriginalStaffCommands;
    public boolean forceToDedicatedChannel;
    public boolean forceToManagementChannel;
    public char commandPrefix;
    public String fieldHeader;
    public Member owner;
    String ownerDiscordID;
    String adminRoleID;
    String staffRoleID;
    String teamRoleID;
    String memberRoleID;
    String teamChannelID;
    String helpChannelID;
    String botSpamChannelID;
    String managementChannelID;
    String logChannelID;
    String dedicatedOutputChannelID;
    public TextChannel discussionChannel;
    public TextChannel helpChannel;
    public TextChannel botSpamChannel;
    public TextChannel managementChannel;
    public TextChannel logChannel;
    public TextChannel dedicatedOutputChannel;
    private Role adminRole;
    private Role staffRole;
    private Role teamRole;
    private List<Role> supporterRoles = new ArrayList<>();
    private Role memberRole;

    String checkIconURL;
    String warningIconURL;
    String errorIconURL;
    String infoIconURL;
    String stopIconURL;
    String helpIconURL;

    int pingCoolDown;

    public long highPingTime;

    String blobNomPingID;

    MainConfiguration(JsonObject importConfigObj) {
        configObj = importConfigObj;
        token = configObj.get("token").getAsString();
        testModeEnabled = configObj.get("testModeEnabled").getAsBoolean();
    }

    void initialSetup() {
        commandPrefix = configObj.get("commandPrefix").getAsString().charAt(0);

        ownerDiscordID = configObj.get("ownerDiscordID").getAsString();
        adminRoleID = configObj.get("adminRole").getAsString();
        staffRoleID = configObj.get("staffRole").getAsString();
        teamRoleID = configObj.get("teamRole").getAsString();
        memberRoleID = configObj.get("memberRole").getAsString();
        teamChannelID = configObj.get("teamDiscussionChannel").getAsString();
        helpChannelID = configObj.get("helpChannel").getAsString();
        managementChannelID = configObj.get("managementChannel").getAsString();
        botSpamChannelID = configObj.get("botSpamChannel").getAsString();
        logChannelID = configObj.get("logChannel").getAsString();
        dedicatedOutputChannelID = configObj.get("dedicatedOutputChannel").getAsString();
        fieldHeader = configObj.get("fieldHeader").getAsString();
        pingCoolDown = configObj.get("pingCoolDownMinutes").getAsInt();

        checkIconURL = configObj.get("checkIconURL").getAsString();
        warningIconURL = configObj.get("warningIconURL").getAsString();
        errorIconURL = configObj.get("errorIconURL").getAsString();
        infoIconURL = configObj.get("infoIconURL").getAsString();
        stopIconURL = configObj.get("stopIconURL").getAsString();
        helpIconURL = configObj.get("helpIconURL").getAsString();

        highPingTime = configObj.get("highPingTime").getAsLong();

        blobNomPingID = configObj.get("blobNomPingID").getAsString();

        deleteOriginalNonStaffCommands = configObj.get("deleteOriginalNonStaffCommands").getAsBoolean();
        deleteOriginalStaffCommands = configObj.get("deleteOriginalStaffCommands").getAsBoolean();
        forceToDedicatedChannel = configObj.get("forceAllNonTeamOutputToDedicatedChannel").getAsBoolean();
        forceToManagementChannel = configObj.get("forceAllTeamOutputToManagementChannel").getAsBoolean();
    }
    void discordSetup() {
        owner = guild.getMemberById(ownerDiscordID);

        discussionChannel = guild.getTextChannelById(teamChannelID);
        helpChannel = guild.getTextChannelById(helpChannelID);
        managementChannel = guild.getTextChannelById(managementChannelID);
        logChannel = guild.getTextChannelById(logChannelID);

        if (!dedicatedOutputChannelID.equalsIgnoreCase("None")) {
            dedicatedOutputChannel = guild.getTextChannelById(dedicatedOutputChannelID);
        }
        if (!botSpamChannelID.equalsIgnoreCase("None")) {
            botSpamChannel = guild.getTextChannelById(botSpamChannelID);
        }
        adminRole = guild.getRoleById(adminRoleID);
        staffRole = guild.getRoleById(staffRoleID);
        teamRole = guild.getRoleById(teamRoleID);

        List<Long> supporterRoleIDs = new Gson().fromJson(configObj.get("supporterRoles").getAsString(), new TypeToken<ArrayList<Long>>(){}.getType());

        supporterRoleIDs.forEach(roleID -> {
            supporterRoles.add(guild.getRoleById(roleID));
        });

        memberRole = guild.getRoleById(memberRoleID);

    }
    public boolean discordGuildConfigurationsExist() {
        return guild.getMembers().contains(guild.getMemberById(ownerDiscordID))
                && guild.getTextChannels().contains(guild.getTextChannelById(teamChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(logChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(helpChannelID))
                && guild.getTextChannels().contains(guild.getTextChannelById(managementChannelID))
                && guild.getRoles().contains(guild.getRoleById(adminRoleID))
                && guild.getRoles().contains(guild.getRoleById(staffRoleID))
                && guild.getRoles().contains(guild.getRoleById(teamRoleID));
    }
    boolean reload(JsonObject importReloadedConfig) {
        configObj = importReloadedConfig;
        initialSetup();
        if (discordGuildConfigurationsExist()) {
            discordSetup();
            return true;
        }
        else return false;
    }

    public Role getAdminRole() {
        return adminRole;
    }

    public Role getStaffRole() {
        return staffRole;
    }

    public Role getTeamRole() {
        return teamRole;
    }

    public List<Role> getSupporterRoles() {
        return supporterRoles;
    }

    public Role getMemberRole() {
        return memberRole;
    }

    public String getWarningIconURL() {
        return warningIconURL;
    }

    public String getCheckIconURL() {
        return checkIconURL;
    }

    public String getErrorIconURL() {
        return errorIconURL;
    }

    public String getInfoIconURL() {
        return infoIconURL;
    }

    public String getStopIconURL() {
        return stopIconURL;
    }

    public String getHelpIconURL() {
        return helpIconURL;
    }

    protected void setAdminRole(Role adminRole) {
        this.adminRole = adminRole;
    }

    protected void setStaffRole(Role staffRole) {
        this.staffRole = staffRole;
    }

    protected void setTeamRole(Role teamRole) {
        this.teamRole = teamRole;
    }

    abstract void setConfig(String key, String value);
    abstract void setConfig(String key, boolean value);
    abstract void setConfig(String key, int value);

    abstract void setRoleConfig(String key, long newRoleID);
    abstract void setRoleConfig(String key, Role newRole);

    abstract void setChannelConfig(String key, long newChannelID);
    abstract void setChannelConfig(String key, TextChannel newChannel);

    abstract boolean isValidConfig(String key);
}