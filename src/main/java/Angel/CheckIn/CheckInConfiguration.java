package Angel.CheckIn;

import Angel.CommonLogic;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;

public abstract class CheckInConfiguration implements CommonLogic {
    private final Gson gson = new Gson();
    private JsonObject configObj;
    private final boolean enabled;
    Role checkInRole;
    TextChannel checkInChannel; // Optional Config - It Is Possible to run a check-in without a dedicated channel for it.
    List<Role> rolesThatCanBeCheckedIn = new ArrayList<>();
    List<Long> rolesThatCanBeCheckedInLong;
    int checkInDuration;
    int checkInUpdate;
    int mentionCheckInRole;

    String checkInChannelID;

    CheckInConfiguration(JsonObject configObj) {
        this.configObj = configObj;
        this.enabled = configObj.get("enabled").getAsBoolean();
    }

    void setup() {
        checkInChannelID = configObj.get("checkInChannelID").getAsString();
        checkInRole = getGuild().getRoleById(configObj.get("checkInRoleID").getAsLong());
        if (!checkInChannelID.equalsIgnoreCase("None")) {
            checkInChannel = getGuild().getTextChannelById(checkInChannelID);
        }
        checkInDuration = configObj.get("checkInDuration").getAsInt();
        checkInUpdate = configObj.get("checkInProgressionUpdateInSeconds").getAsInt();
        mentionCheckInRole = configObj.get("mentionCheckInRoleAtMinute").getAsInt();

        rolesThatCanBeCheckedInLong = gson.fromJson(configObj.get("rolesThatCanBeCheckedInIDs").getAsString(), new TypeToken<List<Long>>(){}.getType());

        rolesThatCanBeCheckedInLong.forEach(roleID -> {
            rolesThatCanBeCheckedIn.add(getGuild().getRoleById(roleID));
        });
    }
    void reload(JsonObject reloadedObject) {
        configObj = reloadedObject;
        setup();
    }
    public abstract int setupRoles();

    public abstract void setCheckInChannel(long channelID);
    public abstract void setCheckInChannel(TextChannel channel);
    public abstract void setCheckInRole(Role role);
    public abstract void setCheckInDuration(int newDuration);

    public boolean isEnabled() {
        return enabled;
    }

    public Role getCheckInRole() {
        return checkInRole;
    }

    public TextChannel getCheckInChannel() {
        return checkInChannel;
    }

    public List<Role> getRolesThatCanBeCheckedIn() {
        return rolesThatCanBeCheckedIn;
    }

    public int getCheckInDuration() {
        return checkInDuration;
    }

    public int getCheckInUpdate() {
        return checkInUpdate;
    }

    public int getWhenMentionCheckInRole() {
        return mentionCheckInRole;
    }
}