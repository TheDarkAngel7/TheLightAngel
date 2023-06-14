package Angel.Nicknames;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Arrays;

class ModifyNickConfiguration extends NickConfiguration implements NickConfig {
    private final ArrayList<String> configs = new ArrayList<>(Arrays.asList("useteamchannelonrequest", "rolesnotallowedtochangename",
            "requestcooldown", "requestcd", "reqcd", "pingstaffonline", "pingstaffonlineonrequest"));

    ModifyNickConfiguration(JsonObject importConfigObj) {
        super(importConfigObj);
    }

    public void setConfig(String key, int newValue) {
        switch (key.toLowerCase()) {
            case "requestCD":
            case "reqCD":
            case "requestCoolDown": requestCoolDown = newValue;
        }
    }

    public void setConfig(String key, boolean newValue) {
        switch (key.toLowerCase()) {
            case "useTeamChannelOnRequest": useTeamChannel = newValue;
            break;
            case "pingStaffOnline":
            case "pingStaffOnlineOnRequest": pingOnlineStaff = newValue;
        }
    }

    public void addNewNameRestrictedRole(long newRoleID) throws NullPointerException {
        addNewNameRestrictedRole(getGuild().getRoleById(newRoleID));
    }

    public void addNewNameRestrictedRole(Role newRole) {
        restrictedRoles.add(newRole);
    }

    public boolean removeNewNameRestrictedRole(long roleToDeleteID) throws NullPointerException {
        return removeNewNameRestrictedRole(getGuild().getRoleById(roleToDeleteID));
    }

    public boolean removeNewNameRestrictedRole(Role roleToDelete) {
        int index = restrictedRoles.indexOf(roleToDelete);
        if (index == -1) return false;
        else {
            restrictedRoles.remove(roleToDelete);
            return true;
        }
    }

    public boolean isValidConfig(String key) {
        return configs.contains(key.toLowerCase());
    }
}