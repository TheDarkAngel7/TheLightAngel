package Angel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;

class FileHandler {

    private final Logger log = LogManager.getLogger(FileHandler.class);

    JsonObject getMainConfig() {
        JsonElement element;
        try {
            element = JsonParser.parseReader(new FileReader("configs/config.json"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        log.info("Main Configuration was Read");
        return element.getAsJsonObject();
    }
}