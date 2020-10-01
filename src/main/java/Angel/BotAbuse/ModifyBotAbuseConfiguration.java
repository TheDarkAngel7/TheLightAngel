package Angel.BotAbuse;

import Angel.MainConfiguration;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

class ModifyBotAbuseConfiguration extends BotAbuseConfiguration {
    private final ArrayList<String> configs = new ArrayList<>(
            Arrays.asList("botabuserole", "rolescanint", "rolescannerinterval", "hotmonths", "hotoffensemonths",
                    "maxdaysundo", "maxdaysallowedforundo"));

    ModifyBotAbuseConfiguration(JsonObject configObj, BotAbuseMain baMain, FileHandler fileHandler, MainConfiguration mainConfig) {
        super(configObj, baMain, fileHandler, mainConfig);
    }


    public void setConfig(String key, int value) {
        switch (key.toLowerCase()) {
            case "rolescanint":
            case "rolescannerinterval": roleScannerInterval = value; break;
            case "hotmonths":
            case "hotoffensemonths": hotOffenseMonths = value; break;
            case "maxdaysundo":
            case "maxdaysallowedforundo": maxDaysAllowedForUndo = value; break;
        }
    }
    public boolean setNewBotAbuseRole(long newRoleID) {
        try {
            botAbuseRole = guild.getRoleById(newRoleID);
            return true;
        }
        catch (NullPointerException ex) {
            return false;
        }
    }

    public void setNewBotAbuseRole(Role newRole) {
        botAbuseRole = newRole;
    }

    public String addExpiryTime(int newTime) {
        botAbuseTimes.add(newTime);
        botAbuseTimes.sort(Comparator.naturalOrder());
        if (baMain.BACore.timingsAreValid()) return "**Successfully Added " + newTime + " Days**\n\n" + getExpiryTimeArray();
        else {
            removeExpiryTime(newTime, true);
            return ":x: **Cannot Add " + newTime + " Days**" +
                    "\n\nYour hotOffenseMonths setting is currently " + hotOffenseMonths + " Months, " +
                    "you cannot add a new time that is greater than half of that. " +
                    "Your total number of days also cannot match or exceed the total number of days in " + hotOffenseMonths + " Months" +
                    "\n*Example: If your hotOffenseMonths is 6 Months, you cannot add a 181 Day offense to that*" +
                    "\n*Example: If your hotOffenseMonths is 6 Months, you cannot have a 60 day and 120 day serve period as that'd be 180 days total*";
        }
    }

    public String removeExpiryTime(int removeThisTime, boolean fromInvalidAdd) {
        int index = botAbuseTimes.indexOf(removeThisTime);
        if (index == -1) return ":x: **Could Not Remove " + removeThisTime + " Days as it does not exist**";
        else {
            botAbuseTimes.remove(index);
            if (!fromInvalidAdd) return "**Successfully Removed " + removeThisTime + " Days**\n\n" + getExpiryTimeArray();
            else return null;
        }
    }
    private String getExpiryTimeArray() {
        String result = "Here's how the new Bot Abuse Punishment Sequence Looks Like:\n";
        int index = 0;
        do {
            if (index == botAbuseTimes.size()) {
                result = result.concat("\n**Offense " + (index++ + 1) + ": " + " Permanent**");
            }
            else result = result.concat("\n**Offense " + (index + 1) + ": " + botAbuseTimes.get(index++) + " Days**");
        } while (index < botAbuseTimes.size() + 1);
        return result;
    }
    public boolean isValidConfig(String key) {
        return configs.contains(key.toLowerCase());
    }
}