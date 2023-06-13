package Angel.BotAbuse;

import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

class ModifyBotAbuseConfiguration extends BotAbuseConfiguration implements BotAbuseConfig {
    private BotAbuseMain baMain;
    private final ArrayList<String> configs = new ArrayList<>(
            Arrays.asList("botabuserole", "rolescanint", "rolescannerinterval", "hotmonths", "hotoffensemonths",
                    "maxdaysundo", "maxdaysallowedforundo", "autoperm", "autopermanent"));

    ModifyBotAbuseConfiguration(FileHandler fileHandler) {
        super(fileHandler.getConfig());
    }

    public void setConfig(String key, int value) {
        switch (key.toLowerCase()) {
            case "rolescanint":
            case "rolescannerinterval": roleScannerInterval = value; break;
            case "hotmonths":
            case "hotoffensemonths": hotOffenseMonths = value; break;
            case "hotwarning":
            case "hotoffensewarning": hotOffenseWarning = value; break;
            case "maxdaysundo":
            case "maxdaysallowedforundo": maxDaysAllowedForUndo = value; break;
        }
    }
    public void setConfig(String key, boolean value) {
        switch (key.toLowerCase()) {
            case "autoperm":
            case "autopermanent": autoPermanent = value;
        }
    }
    public boolean setNewBotAbuseRole(long newRoleID) {
        if (getGuild().getRoleById(newRoleID) != null) {
            botAbuseRole = getGuild().getRoleById(newRoleID);
            return true;
        }
        else return false;
    }

    public void setNewBotAbuseRole(Role newRole) {
        botAbuseRole = newRole;
    }

    public String addExpiryTime(int newTime) {
        botAbuseTimes.add(newTime);
        botAbuseTimes.sort(Comparator.naturalOrder());
        if (baMain.getCore().timingsAreValid()) return "**Successfully Added " + newTime + " Days**\n\n" + getExpiryTimeArray();
        else {
            removeExpiryTime(newTime, true);
            return ":x: **Cannot Add " + newTime + " Days**" +
                    "\n\nYour hotOffenseMonths setting is currently " + hotOffenseMonths + " Months, " +
                    "you cannot add a new time that is greater than half of that. " +
                    "Your total number of days also cannot match or exceed the total number of days in " + hotOffenseMonths + " Months." +
                    "\nAlso your maxDaysAllowedForUndo setting cannot exceed any of the timings in the Bot Abuse Timings." +
                    "\n\n*Example: If your hotOffenseMonths is 6 Months, you cannot add a 181 Day offense to that.*" +
                    "\n*Example: If your hotOffenseMonths is 6 Months, you cannot have a 60 day and 120 day serve period as that'd be 180 days total.*" +
                    "\n*Example: If your maxDaysAllowedForUndo is currently 5 days, you cannot had a 3 day bot abuse as you would then be able to undo a bot abuse after expiring.*";
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