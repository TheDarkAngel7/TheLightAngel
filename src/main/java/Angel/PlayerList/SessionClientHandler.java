package Angel.PlayerList;

import Angel.CommonLogic;
import Angel.Exceptions.InvalidSessionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;

public class SessionClientHandler implements Runnable, CommonLogic {
    private final Logger log = LogManager.getLogger(SessionClientHandler.class);

    private final Socket clientSocket;
    private final Gson gson = new Gson();

    public SessionClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            JsonObject jsonObject = JsonParser.parseReader(new InputStreamReader(clientSocket.getInputStream())).getAsJsonObject();
            String hostName = jsonObject.get("hostName").getAsString();
            List<String> playerList = gson.fromJson(jsonObject.get("players").getAsString(), new TypeToken<List<String>>(){}.getType());

            log.info("Received a Player List for {} with {} players on it!", hostName, playerList.size() - 1);

            switch (jsonObject.get("sessionState").getAsString().toLowerCase()) {
                case "offline":
                    sessionManager.setSessionState(hostName, SessionStatus.OFFLINE);
                    break;
                case "restart":
                    sessionManager.setSessionState(hostName, SessionStatus.RESTARTING);
                    break;
                    // Online we'll update the player list too.
                default:
                    sessionManager.setSessionPlayers(hostName, playerList);
            }


        } catch (InvalidSessionException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
