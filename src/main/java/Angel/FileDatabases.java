package Angel;

import com.google.gson.JsonObject;

import java.io.IOException;

public interface FileDatabases {

    JsonObject getConfig() throws IOException;

    void getDatabase() throws IOException;

    void saveDatabase() throws IOException;
}
