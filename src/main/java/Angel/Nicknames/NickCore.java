package Angel.Nicknames;

import net.dv8tion.jda.api.entities.Guild;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

class NickCore {
    private final Logger log = LogManager.getLogger(NickCore.class);
    Angel.Nicknames.FileHandler fileHandler;
    private Guild guild;
    private NickConfiguration nickConfig;
    ArrayList<Integer> requestID = new ArrayList<>();
    ArrayList<Long> discordID = new ArrayList<>();
    ArrayList<String> oldNickname = new ArrayList<>();
    ArrayList<String> newNickname = new ArrayList<>();
    Dictionary<Long, ArrayList<String>> oldNickDictionary = new Hashtable<>();
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
            fileHandler.fileReader.close();
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
                "\nDiscord: <@!" + targetDiscordID + ">";

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
        else {
            int index = requestID.indexOf(id);
            requestID.remove(index);
            discordID.remove(index);
            oldNickname.remove(index);
            newNickname.remove(index);
            return "None";
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
        String[] nicknames = null;
        try {
            if (targetDiscordID == -1) {
                nicknames = getDataAtIndexAndRemove(requestID.indexOf(targetRequestID));
            }
            else {
                nicknames = getDataAtIndexAndRemove(discordID.indexOf(targetDiscordID));
            }
        }
        catch (IndexOutOfBoundsException ex) {
            return null;
        }
        if (nicknames[0].contains("FATAL ERROR")) {
            return "FATAL ERROR";
        }
        else {
            String result = "Successfully Accepted the Request";
            if (targetDiscordID == -1) {
                result = result.concat("\nDiscord: <@!" + nicknames[1] + ">"
                        + "\nOld Nickname: **" + nicknames[2]
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
    String withdrawRequest(long targetDiscordID, boolean triggeredOnGuildLeave, boolean triggeredOnRoleRemove) throws IOException {
        int index = discordID.indexOf(targetDiscordID);
        if (index == -1) return null;
        String result = "";
        String defaultReturn = "Successfully Withdrew Nickname Request for ?";
        defaultReturn = defaultReturn.concat(
                        "\n\n**Request Details:**" +
                        "\nRequest ID: **" + requestID.remove(index) +
                        "**\nDiscord: <@!" + discordID.remove(index) + ">" +
                        "\nOld Nickname: **" + oldNickname.remove(index) +
                        "**\nNew Nickname: **" + newNickname.remove(index)) + "**";
        if (arraySizesEqual()) {
            fileHandler.saveDatabase();

            if (!triggeredOnGuildLeave && !triggeredOnRoleRemove) return defaultReturn;
            else if (triggeredOnGuildLeave && !triggeredOnRoleRemove) {
                String returnValue = defaultReturn.replace("?", String.valueOf(targetDiscordID).concat(" who left the Discord Server"));
                return returnValue;
            }
            else if (!triggeredOnGuildLeave && triggeredOnRoleRemove) {
                String returnValue = defaultReturn.replace("?", "? who had all nickname restricted roles removed.");

                return returnValue;
            }
            else {
                log.fatal("Boolean Values in withdrawing requests were both unexpectedly true");
                return "FATAL ERROR: Boolean values were messed up";
            }
        }
        else return "FATAL ERROR";
    }
    String getList() {
        int index = 0;
        String result = "";
        if (discordID.isEmpty()) return null;
        while (index < discordID.size()) {
            result = result.concat(replaceNulls(discordID.get(index),
                    "\n\nRequest ID: **" + requestID.get(index) +
                    "**\nDiscord Account: <@!" + discordID.get(index) + ">" +
                    "\nOld Nickname: **" + oldNickname.get(index) +
                    "**\nNew Nickname: **" + newNickname.get(index++) + "**"));
        }
        return result;
    }
    String replaceNulls(long targetDiscordID, String oldString) {
        String result = "";
        if (oldString.contains("New Nickname: **null**")) {
            result = oldString.replace("null",
                    guild.getMemberById(targetDiscordID).getUser().getName() + " (Reset to Discord Username)");
        }
        else if (oldString.contains("Old Nickname: **null**")) {
            result = oldString.replace("null",
                    guild.getMemberById(targetDiscordID).getUser().getName() + " (Current Discord Username)");
        }
        return result;
    }
    private String[] getDataAtIndexAndRemove(int index) throws IOException, IndexOutOfBoundsException {
        String returnValue = requestID.remove(index) + "," + discordID.remove(index) + ","
                + oldNickname.remove(index) + "," + newNickname.remove(index);
        if (!arraySizesEqual()) {
            String[] value = new String[1];
            value[0] = "FATAL ERROR";
            return value;
        }
        else {
            return returnValue.split(",");
        }
    }
    ArrayList<String> getHistory(long targetDiscordID) {
        return oldNickDictionary.get(targetDiscordID);
    }
    boolean arraySizesEqual() {
        return requestID.size() == discordID.size() && oldNickname.size() == newNickname.size();
    }
}