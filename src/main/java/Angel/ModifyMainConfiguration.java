package Angel;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.ArrayList;
import java.util.Arrays;

class ModifyMainConfiguration extends MainConfiguration {
    private final ArrayList<String> configs = new ArrayList<>(Arrays.asList("timezone", "checkiconurl", "warningiconurl",
            "erroriconurl", "infoiconurl", "stopiconurl", "helpiconurl", "blobnompingid", "commandprefix", "cmdprefix",
            "fieldheader", "deleteoriginalnonstaffcommands", "delnonstaffcmd", "deleteoriginalstaffcommands", "delstaffcmd",
            "forcetolightangelchannel", "forcetola", "forcetodedicatedchannel", "forcedc","pingcooldown", "pingcd",
            "highpingtime", "highping", "adminrole", "staffrole", "teamrole", "teamdiscussionchannel", "discussionchannel",
            "helpchannel", "botspamchannel", "botmanagementchannel", "managementchannel", "logchannel", "thelightangelchannel",
            "dedicatedoutputchannel"));
    ModifyMainConfiguration(JsonObject importConfigObj) {
        super(importConfigObj);
    }
    void setConfig(String key, String value) {
        switch (key.toLowerCase()) {
            case "checkiconurl": checkIconURL = value; break;
            case "warningiconurl": warningIconURL = value; break;
            case "erroriconurl": errorIconURL = value; break;
            case "infoiconurl": infoIconURL = value; break;
            case "stopiconurl": stopIconURL = value; break;
            case "helpiconurl": helpIconURL = value; break;
            case "blobnompingid": blobNomPingID = value; break;
            case "commandprefix":
            case "cmdprefix": commandPrefix = value.charAt(0); break;
            case "fieldheader": fieldHeader = value;
        }
    }
    void setConfig(String key, boolean value) {
        switch (key.toLowerCase()) {
            case "deleteoriginalnonstaffcommands":
            case "delnonstaffcmd": deleteOriginalNonStaffCommands = value; break;
            case "deleteoriginalstaffcommands":
            case "delstaffcmd": deleteOriginalStaffCommands = value; break;
            case "forcetolightangelchannel":
            case "forcetola":
            case "forcetodedicatedchannel":
            case "forcedc": forceToDedicatedChannel = value; break;
            case "forcetomanagementchannel":
            case "forcetomc": forceToManagementChannel = value; break;
        }
    }
    void setConfig(String key, int value) {
        switch (key.toLowerCase()) {
            case "pingcooldown":
            case "pingcd": pingCoolDown = value; break;
            case "highpingtime":
            case "highping": highPingTime = Long.parseLong(String.valueOf(value)); break;
        }

    }
    void setRoleConfig(String key, Role newRole) {
        switch (key.toLowerCase()) {
            case "adminrole": adminRole = newRole; break;
            case "staffrole": staffRole = newRole; break;
            case "teamrole": teamRole = newRole; break;
        }
    }
    void setRoleConfig(String key, long newRoleID) throws NullPointerException {
        setRoleConfig(key, guild.getRoleById(newRoleID));
    }
    void setChannelConfig(String key, TextChannel newChannel) {
        switch (key.toLowerCase()) {
            case "teamdiscussionchannel":
            case "discussionchannel": discussionChannel = newChannel; break;
            case "helpchannel": helpChannel = newChannel; break;
            case "botspamchannel": botSpamChannel = newChannel; break;
            case "botmanagementchannel":
            case "managementchannel": managementChannel = newChannel; break;
            case "logchannel": logChannel = newChannel; break;
            case "thelightangelchannel":
            case "dedicatedoutputchannel": dedicatedOutputChannel = newChannel; break;
        }
    }
    void setChannelConfig(String key, long newChannelID) throws NullPointerException {
        setChannelConfig(key, guild.getTextChannelById(newChannelID));
    }
    boolean isValidConfig(String key) {
        return configs.contains(key.toLowerCase());
    }
}
