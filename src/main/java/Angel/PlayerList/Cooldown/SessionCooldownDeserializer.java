package Angel.PlayerList.Cooldown;

import Angel.ZoneIDInstanceCreator;
import Angel.ZonedDateTimeAdapter;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SessionCooldownDeserializer implements JsonDeserializer<SessionCooldownConfiguration> {

    @Override
    public SessionCooldownConfiguration deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // 1. Let GSON deserialize all the standard @Expose fields normally
        // We use a clean factory method or standard context to prevent infinite loops
        SessionCooldownConfiguration config = new GsonBuilder()
                .registerTypeAdapter(ZoneId.class, new ZoneIDInstanceCreator())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .create()
                .fromJson(json, SessionCooldownConfiguration.class);

        // 2. CRITICAL: Re-initialize the logger manually using reflection since it's private final
        try {
            Field logField = SessionCooldownConfiguration.class.getDeclaredField("log");
            logField.setAccessible(true);
            logField.set(config, LogManager.getLogger(SessionCooldownConfiguration.class));
        } catch (Exception e) {
            throw new JsonParseException("Failed to inject logger into configuration", e);
        }

        return config;
    }
}
