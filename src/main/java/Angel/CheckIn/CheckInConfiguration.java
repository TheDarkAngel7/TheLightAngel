package Angel.CheckIn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.ArrayList;
import java.util.List;

abstract class CheckInConfiguration {
    Guild guild;
    private final Gson gson = new Gson();
    private JsonObject configObj;
    private final boolean enabled;
    Role checkInRole;
    TextChannel checkInChannel; // Optional Config - It Is Possible to run a check-in without a dedicated channel for it.
    List<Role> rolesThatCanBeCheckedIn = new ArrayList<>();
    List<Long> rolesThatCanBeCheckedInLong;
    int checkInDuration;
    int checkInUpdate;

    String checkInChannelID;

    CheckInConfiguration(Guild guild, JsonObject configObj) {
        this.guild = guild;
        this.configObj = configObj;
        this.enabled = configObj.get("enabled").getAsBoolean();
    }

    void setup() {
        checkInChannelID = configObj.get("checkInChannelID").getAsString();
        checkInRole = guild.getRoleById(configObj.get("checkInRoleID").getAsLong());
        if (!checkInChannelID.equalsIgnoreCase("None")) {
            checkInChannel = guild.getTextChannelById(checkInChannelID);
        }
        checkInDuration = configObj.get("checkInDuration").getAsInt();
        checkInUpdate = configObj.get("checkInProgressionUpdateInSeconds").getAsInt();

        rolesThatCanBeCheckedInLong = gson.fromJson(configObj.get("rolesThatCanBeCheckedInIDs").getAsString(), new TypeToken<List<Long>>(){}.getType());

    }
    void reload(JsonObject reloadedObject) {
        configObj = reloadedObject;
        setup();
    }
    abstract int setupRoles();

    abstract void setCheckInChannel(long channelID);
    abstract void setCheckInChannel(TextChannel channel);
    abstract void setCheckInRole(Role role);
    abstract void setCheckInDuration(int newDuration);

    boolean isEnabled() {
        return enabled;
    }

    Role getCheckInRole() {
        return checkInRole;
    }

    TextChannel getCheckInChannel() {
        return checkInChannel;
    }

    List<Role> getRolesThatCanBeCheckedIn() {
        return rolesThatCanBeCheckedIn;
    }

    int getCheckInDuration() {
        return checkInDuration;
    }

    int getCheckInUpdate() {
        return checkInUpdate;
    }
}