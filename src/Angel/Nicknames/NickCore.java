package Angel.Nicknames;

import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import java.io.IOException;
import java.util.ArrayList;

class NickCore {
    private final Logger log = LogManager.getLogger(NickCore.class);
    private Angel.Nicknames.FileHandler fileHandler;
    private Guild guild;
    private NickConfiguration nickConfig;
    ArrayList<Integer> requestID = new ArrayList<>();
    ArrayList<Long> discordID = new ArrayList<>();
    ArrayList<String> oldNickname = new ArrayList<>();
    ArrayList<String> newNickname = new ArrayList<>();

    NickCore(Guild importGuild) {
        fileHandler = new FileHandler(this);
        guild = importGuild;
    }

    void startup() throws IOException {
        log.info("Nickname Core Initiated...");
        try {
            fileHandler.getDatabase();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Nickname Arrays - Data File is Empty");
        }
    }
    void setNickConfig(NickConfiguration importedNickConfig) {
        nickConfig = importedNickConfig;
    }

    String submitRequest(long targetDiscordID, @Nullable String oldNick, @Nullable String newNick) throws IOException {
        int id;
        do {
            id = (int) (Math.random() * 1000);
        } while (requestID.contains(id) || id < 100);

        requestID.add(id);
        discordID.add(targetDiscordID);
        oldNickname.add(oldNick);
        newNickname.add(newNick);

        String result =
                "**:white_check_mark: New Nickname Change Request Received**" +
                "\n\nID: " + id +
                "\nDiscord ID: " + targetDiscordID;

        if (oldNick != null && newNick != null) {
            result = result.concat(
                    "\nOld Nickname: **" + oldNick +
                            "**\nNew Nickname: **" + newNick + "**"
            );
        }
        else if (oldNick == null && newNick != null) {
            result = result.concat(
                    "\nOld Nickname: **" + guild.getMemberById(targetDiscordID).getEffectiveName() + " (No Previous Nickname)**" +
                            "\nNew Nickname: **" + newNick + "**"
            );
        }
        else if (oldNick != null && newNick == null) {
            result = result.concat(
                    "\nOld Nickname: **" + oldNick +
                            "**\nNew Nickname: **" + guild.getMemberById(targetDiscordID).getUser().getName() + " (Reset to Discord Username)**"
            );
        }
        // oldNick and newNick is impossible to be both null,
        // because that'd be considered a discord name change, nicknames would not be involved

        if (arraySizesEqual()) {
            fileHandler.saveDatabase();
            return result;
        }
        else return ":x: FATAL ERROR: While submitting request, something didn't run correctly";

    }

    String acceptRequest(long targetDiscordID, int targetRequestID) throws IOException {
        String returnValue = "";
        try {
            if (targetDiscordID == -1) {
                returnValue = getDataAtIndexAndRemove(requestID.indexOf(targetRequestID));
            }
            else {
                returnValue = getDataAtIndexAndRemove(discordID.indexOf(targetDiscordID));
            }
        }
        catch (IndexOutOfBoundsException ex) {
            return null;
        }
        if (returnValue.contains("FATAL ERROR")) {
            return "FATAL ERROR";
        }
        else {
            String[] nicknames = returnValue.split(",");
            String result = "Successfully Accepted the Request";
            if (targetDiscordID == -1) {
                result = result.concat("\nDiscord ID: **" + nicknames[1]
                        + "**\nOld Nickname: **" + nicknames[2]
                        + "**\nNew Nickname: **" + nicknames[3] + "**");
            }
            else {
                result = result.concat("\nRequest ID: **" + nicknames[0]
                        + "**\nOld Nickname: **" + nicknames[2]
                        + "**\nNew Nickname: **" + nicknames[3] + "**");
            }
            return result;
        }
    }

    String denyRequest(long targetDiscordID, int targetRequestID) throws IOException {
        // We're using the same execution as accepting the request did but we're going to replace "Accepted" with "Rejected"
        String result = acceptRequest(targetDiscordID, targetRequestID);
        if (result == null) return null;
        result = result.replace("Accepted", "Rejected");
        return result;
    }
    String withdrawRequest(long targetDiscordID, boolean triggeredOnGuildLeave) throws IOException {
        int index = discordID.indexOf(targetDiscordID);
        if (index == -1) return null;
        String result = "";

        requestID.remove(index);
        discordID.remove(index);
        oldNickname.remove(index);
        newNickname.remove(index);

        if (arraySizesEqual()) {
            fileHandler.saveDatabase();
            String defaultReturn = "Successfully Withdrew Nickname Request for ?";
            if (!triggeredOnGuildLeave) return defaultReturn;
            else {
                String returnValue = defaultReturn.replace("?", String.valueOf(targetDiscordID));
                returnValue = returnValue.concat(" who left the Discord Server");
                return returnValue;
            }
        }
        else return "FATAL ERROR";
    }
    String getList(Guild guild) {
        int index = 0;
        String result = "";
        if (discordID.isEmpty()) return null;
        while (index < discordID.size()) {
            result = result.concat(
                    "\n\nRequest ID: **" + requestID.get(index) +
                            "**\nDiscord ID: **" + guild.getMemberById(discordID.get(index)).getIdLong() +
                            "**\nOld Nickname: **" + oldNickname.get(index) +
                            "**\nRequested New Nickname: **" + newNickname.get(index++) + "**"
            );
            result = result.replaceAll("null", "None");
        }
        return result;
    }
    private String getDataAtIndexAndRemove(int index) throws IOException, IndexOutOfBoundsException {
        String returnValue = requestID.get(index) + "," + discordID.get(index) + "," + oldNickname.get(index) + "," + newNickname.get(index);
        requestID.remove(index);
        discordID.remove(index);
        oldNickname.remove(index);
        newNickname.remove(index);
        if (!arraySizesEqual()) {
            return "FATAL ERROR: Error Occured While Getting the Nickname at target index and removing";
        }
        else {
            fileHandler.saveDatabase();
            return returnValue;
        }
    }
    boolean arraySizesEqual() {
        return requestID.size() == discordID.size() && oldNickname.size() == newNickname.size();
    }
}