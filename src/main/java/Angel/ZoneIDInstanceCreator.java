package Angel;

import com.google.gson.InstanceCreator;

import java.lang.reflect.Type;
import java.time.ZoneId;

public class ZoneIDInstanceCreator implements InstanceCreator<ZoneId> {
    @Override
    public ZoneId createInstance(Type type) {
        return ZoneId.of("UTC");
    }
}
