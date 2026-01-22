package Angel.PlayerList;

import Angel.Exceptions.InvalidSessionException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.Normalizer;
import java.util.Base64;
import java.util.List;

public class SessionClientHandler implements Runnable, PlayerListLogic {
    private final Logger log = LogManager.getLogger(SessionClientHandler.class);

    private final Socket clientSocket;
    private final Gson gson = new Gson();

    public SessionClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (clientSocket;
            InputStreamReader in = new InputStreamReader(clientSocket.getInputStream())) {
            JsonElement jsonElement = JsonParser.parseReader(in).getAsJsonObject();
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            clientSocket.close();

            String hostName = formatCleaner(jsonObject.get("hostName").getAsString());
            List<String> playerList = gson.fromJson(jsonObject.get("players").getAsString(), new TypeToken<List<String>>(){}.getType());
            String rawImageString = jsonObject.get("rawImage").getAsString();


            int index = 0;

            do {
                playerList.set(index, formatCleaner(playerList.get(index++)));
            } while (index < playerList.size());

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
                    sessionManager.setSessionPlayers(hostName, playerList, decodeImage(rawImageString));
            }


        }
        catch (InvalidSessionException | Exception e) {
            log.error("Error while handing P2P Session Update - {}: {}",e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String formatCleaner(String input) {
        return Normalizer.normalize(input.replaceAll("[^a-zA-Z0-9 ]", "")
                .replaceAll("\\s+", " ").replaceAll("[^\\p{ASCII}]", ""), Normalizer.Form.NFD);
    }
    private BufferedImage decodeImage(String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                return ImageIO.read(bis);
            }
        }
        catch (IOException e) {
            log.error("Failed to decode image from JSON: {}", e.getMessage());
            return null;
        }
    }
}
