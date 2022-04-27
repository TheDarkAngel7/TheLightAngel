package Angel.CheckIn;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

class ModifyCheckInConfiguration extends CheckInConfiguration {

    ModifyCheckInConfiguration(Guild guild, JsonObject json) {
        super(guild, json);
    }

    // Return 0 if no errors were found
    // Return 1 if errors were found but the role list is still usable
    // Return 2 if errors were found and the role list is not usable
    @Override
    public int setupRoles() {
        int index = 0;
        boolean error = false;

        while (index < rolesThatCanBeCheckedInLong.size()) {
            Role role = guild.getRoleById(rolesThatCanBeCheckedInLong.get(index));
            if (role != null) {
                rolesThatCanBeCheckedIn.add(role);
            }
            else {
                error = true;
            }
            index++;
        }
        if (!rolesThatCanBeCheckedIn.isEmpty() && !error) return 0;
        else if (!rolesThatCanBeCheckedIn.isEmpty()) return 1;
        else return 2;
    }

    @Override
    public void setCheckInChannel(long channelID) {
        checkInChannel = guild.getTextChannelById(channelID);
    }

    @Override
    public void setCheckInChannel(TextChannel channel) {
        checkInChannel = channel;
    }

    @Override
    public void setCheckInRole(Role role) {

    }

    @Override
    public void setCheckInDuration(int newDuration) {

    }
}
