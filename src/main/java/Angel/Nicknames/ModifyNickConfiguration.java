package Angel.Nicknames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Arrays;

class ModifyNickConfiguration extends NickConfiguration {
    private final ArrayList<String> configs = new ArrayList<>(Arrays.asList("rolesNotAllowedToChangeName",
            "requestCoolDown", "requestCD", "reqCD", "pingStaffOnline", "pingStaffOnlineOnRequest"));

    ModifyNickConfiguration(JsonObject importConfigObj, Gson importGsonInstance, Guild importGuild) {
        super(importConfigObj, importGsonInstance, importGuild);
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
            case "pingStaffOnline":
            case "pingStaffOnlineOnRequest": pingOnlineStaff = newValue;
        }
    }

    public void addNewNameRestrictedRole(long newRoleID) {
        restrictedRoles.add(guild.getRoleById(newRoleID));
    }

    public void addNewNameRestrictedRole(Role newRole) {
        restrictedRoles.add(newRole);
    }

    public boolean removeNewNameRestrictedRole(long roleToDelete) {
        Role role = guild.getRoleById(roleToDelete);
        int index = restrictedRoles.indexOf(role);
        if (index == -1) return false;
        else {
            restrictedRoles.remove(role);
            return true;
        }
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