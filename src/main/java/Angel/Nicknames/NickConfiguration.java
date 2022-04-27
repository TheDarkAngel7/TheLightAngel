package Angel.Nicknames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class NickConfiguration {
    private final Gson gson;
    Guild guild;
    private JsonObject configObj;
    private final boolean enabled;
    List<Role> restrictedRoles = new ArrayList<>();
    List<Long> restrictedRolesLong;
    int requestCoolDown;
    boolean useTeamChannel;
    boolean pingOnlineStaff;
    private final Type longType = new TypeToken<ArrayList<Long>>(){}.getType();

    NickConfiguration(JsonObject importConfigObj, Gson importGsonInstance, Guild importGuild) {
        configObj = importConfigObj;
        gson = importGsonInstance;
        guild = importGuild;
        enabled = configObj.get("enabled").getAsBoolean();
    }
    void setup() {
        restrictedRolesLong = gson.fromJson(configObj.get("rolesNotAllowedToChangeName").getAsString(), longType);
        requestCoolDown = configObj.get("requestCoolDownInMinutes").getAsInt();
        pingOnlineStaff = configObj.get("pingStaffOnlineOnRequest").getAsBoolean();
        useTeamChannel = configObj.get("useTeamChannelOnRequest").getAsBoolean();
    }
    void reload(JsonObject reloadedObject) {
        configObj = reloadedObject;
        setup();
    }
    public abstract void setConfig(String key, int newValue);
    public abstract void setConfig(String key, boolean newValue);
    public abstract void addNewNameRestrictedRole(long newRoleID);
    public abstract void addNewNameRestrictedRole(Role newRole);
    public abstract boolean removeNewNameRestrictedRole(long roleToDelete);
    public abstract boolean removeNewNameRestrictedRole(Role roleToDelete);
    public abstract boolean isValidConfig(String key);

    public boolean isEnabled() {
        return enabled;
    }
}
