package Angel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class FileHandler {

    private final Logger log = LogManager.getLogger(FileHandler.class);

    JsonObject getMainConfig() throws FileNotFoundException {
        JsonElement element = JsonParser.parseReader(new FileReader("configs/config.json"));
        log.info("Main Configuration was Read");
        return element.getAsJsonObject();
    }
}