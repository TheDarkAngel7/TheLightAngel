package Angel.Nicknames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

abstract class NickConfiguration {
    Gson gson;
    JsonObject configObj;
    ArrayList<Long> restrictedRoles;
    private Type longType = new TypeToken<ArrayList<Long>>(){}.getType();

    NickConfiguration(JsonObject importConfigObj, Gson importGsonInstance) {
        configObj = importConfigObj;
        gson = importGsonInstance;
    }
    void setup() {
        restrictedRoles = gson.fromJson(configObj.get("rolesNotAllowedToChangeName").getAsString(), longType);
    }
    void reload(JsonObject reloadedObject) {
        configObj = reloadedObject;
        setup();
    }
}
