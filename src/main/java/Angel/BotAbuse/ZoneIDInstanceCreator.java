package Angel.BotAbuse;

import com.google.gson.InstanceCreator;

import java.lang.reflect.Type;
import java.time.ZoneId;

class ZoneIDInstanceCreator implements InstanceCreator<ZoneId> {
    @Override
    public ZoneId createInstance(Type type) {
        return ZoneId.of("UTC");
    }
}
