package Angel.Nicknames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

abstract class NickConfiguration {
    private final Gson gson;
    private JsonObject configObj;
    ArrayList<Long> restrictedRoles;
    int requestCoolDown;
    boolean pingOnlineStaff;
    private final Type longType = new TypeToken<ArrayList<Long>>(){}.getType();

    NickConfiguration(JsonObject importConfigObj, Gson importGsonInstance) {
        configObj = importConfigObj;
        gson = importGsonInstance;
    }
    void setup() {
        restrictedRoles = gson.fromJson(configObj.get("rolesNotAllowedToChangeName").getAsString(), longType);
        requestCoolDown = configObj.get("requestCoolDownInMinutes").getAsInt();
        pingOnlineStaff = configObj.get("pingEveryoneOnlineOnRequest").getAsBoolean();
    }
    void reload(JsonObject reloadedObject) {
        configObj = reloadedObject;
        setup();
    }
}
