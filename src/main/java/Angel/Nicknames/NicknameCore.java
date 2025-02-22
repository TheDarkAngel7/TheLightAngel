package Angel.Nicknames;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class NicknameCore implements NickLogic {
    private final Logger log = LogManager.getLogger(NicknameCore.class);
    Angel.Nicknames.FileHandler fileHandler;
    private Guild guild = getGuild();
    List<Integer> requestID = new ArrayList<>();
    List<Long> discordID = new ArrayList<>();
    List<String> oldNickname = new ArrayList<>();
    List<String> newNickname = new ArrayList<>();
    Dictionary<Long, List<String>> oldNickDictionary = new Hashtable<>();

    NicknameCore() {
        fileHandler = new FileHandler();
    }

    void startup() throws IOException {
        log.info("Nickname Core Initiated...");
        try {
            requestID = fileHandler.getRequestID();
            discordID = fileHandler.getRequestDiscordIDs();
            oldNickname = fileHandler.getOldNicknamesRequests();
            newNickname = fileHandler.getNewNicknamesRequests();
            oldNickDictionary = fileHandler.getNameHistoryDictionary();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Nickname Arrays - Data File is Empty");
        }
    }

    void reloadConfig() {
        nickConfig.reload(fileHandler.getConfig());
    }

    String submitRequest(Member targetMember, String newNick) throws IOException {

        int id;
        do {
            id = (int) (Math.random() * 1000);
        } while (requestID.contains(id) || id < 100);

        requestID.add(id);
        discordID.add(targetMember.getIdLong());
        oldNickname.add(targetMember.getEffectiveName());
        newNickname.add(newNick);

        String result =
                "**:white_check_mark: New Nickname Change Request Received**" +
                "\n\nID: **" + id +
                "**\nDiscord: " + targetMember.getAsMention();

        if (targetMember.getNickname() != null && newNick != null) {
            result = result.concat(
                    "\nOld Nickname: **" + targetMember.getNickname() +
                            "**\nNew Nickname: **" + newNick + "**"
            );
        }
        else if (targetMember.getNickname() == null && newNick != null) {

            if (targetMember.getUser().getGlobalName() != null) {
                result = result.concat(
                        "\nOld Nickname: **" + targetMember.getEffectiveName() + " (Global Display Name)**" +
                                "\nNew Nickname: **" + newNick + "**"
                );

                if (newNick == targetMember.getUser().getName()) {
                    result = result.replace("**" + newNick + "**", "**" + newNick + " (Discord Username)");
                }
            }

            else {
                result = result.concat(
                        "\nOld Nickname: **" + targetMember.getEffectiveName() + " (Discord Username)**" +
                                "\nNew Nickname: **" + newNick + "**"
                );
            }
        }
        else if (targetMember.getNickname() != null && newNick == null) {

            if (targetMember.getUser().getGlobalName() != null) {
                result = result.concat(
                        "\nOld Nickname: **" + targetMember.getNickname() +
                                "**\nNew Nickname: **" + targetMember.getUser().getGlobalName() + " (Reset to Global Display Name)**"
                );
            }

            else {
                result = result.concat(
                        "\nOld Nickname: **" + targetMember.getNickname() +
                                "**\nNew Nickname: **" + targetMember.getUser().getName() + " (Reset to Discord Username)**"
                );
            }
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
            saveDatabase();
            return result;
        }
        else return ":x: FATAL ERROR: While submitting request, something didn't run correctly";
    }

    String acceptRequest(long targetDiscordID, int targetRequestID) {
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
                targetDiscordID = Long.parseLong(nicknames[1]);
                result = result.concat("\nDiscord: <@!" + nicknames[1] + ">"
                        + "\nOld Nickname: **" + nicknames[2]
                        + "**\nNew Nickname: **" + nicknames[3] + "**");
            }
            else {
                result = result.concat("\nRequest ID: **" + nicknames[0]
                        + "**\nOld Nickname: **" + nicknames[2]
                        + "**\nNew Nickname: **" + nicknames[3] + "**");
            }
            return replaceNulls(targetDiscordID, result);
        }
    }

    String denyRequest(long targetDiscordID, int targetRequestID, boolean denySilent) {
        // We're using the same execution as accepting the request did but we're going to replace "Accepted" with "Rejected"
        String result = acceptRequest(targetDiscordID, targetRequestID);
        if (result == null) return null;

        if (!denySilent) result = result.replace("Accepted", "Rejected");
        else result.replace("Accepted", "Rejected Silently");
        return result;
    }
    String withdrawRequest(long targetDiscordID, boolean triggeredOnGuildLeave, boolean triggeredOnRoleRemove) {
        int index = discordID.indexOf(targetDiscordID);
        if (index == -1) return null;
        String result = "";
        String defaultReturn = "Successfully Withdrew Nickname Request for ?";
        defaultReturn = defaultReturn.concat(replaceNulls(discordID.get(index),
                        "\n\n**Request Details:**" +
                        "\nRequest ID: **" + requestID.remove(index) +
                        "**\nDiscord: <@!" + discordID.remove(index) + ">" +
                        "\nOld Nickname: **" + oldNickname.remove(index) +
                        "**\nNew Nickname: **" + newNickname.remove(index) + "**"));
        if (arraySizesEqual()) {
            saveDatabase();

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
                    "Request ID: **" + requestID.get(index) +
                    "**\nDiscord Account: <@!" + discordID.get(index) + ">" +
                    "\nOld Nickname: **" + oldNickname.get(index) +
                    "**\nNew Nickname: **" + newNickname.get(index++) + "**"));
            if (index <= discordID.size() - 1) result = result.concat("\n\n");
        }
        return result;
    }
    private String replaceNulls(long targetDiscordID, String oldString) {
        AtomicReference<User> targetUser = new AtomicReference<>();
        if (targetDiscordID != -1) guild.getJDA().retrieveUserById(targetDiscordID).queue(user -> targetUser.set(user));
        String result = oldString;
        if (oldString.contains("New Nickname: **null**")) {
            result = oldString.replace("null",
                    targetUser.get().getName() + " (Reset to Discord Username)");
        }
        else if (oldString.contains("Old Nickname: **null**")) {
            result = oldString.replace("null",
                    targetUser.get().getName() + " (Current Discord Username)");
        }
        return result;
    }
    private String[] getDataAtIndexAndRemove(int index) throws IndexOutOfBoundsException {
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
    List<String> getHistory(long targetDiscordID) {
        return oldNickDictionary.get(targetDiscordID);
    }
    String clearNameHistory(long targetDiscordID) throws IOException {
        String defaultReturn = "**Successfully Cleared The Name History of <@" + targetDiscordID + ">**";
        oldNickDictionary.remove(targetDiscordID);
        log.info("Successfully Cleared the Name History for " + guild.getMemberById(targetDiscordID).getEffectiveName());
        saveDatabase();
        return defaultReturn;
    }
    boolean arraySizesEqual() {
        return requestID.size() == discordID.size() && oldNickname.size() == newNickname.size();
    }
    void saveDatabase() {
        fileHandler.saveDatabase(requestID, discordID, oldNickname, newNickname, oldNickDictionary);
    }
}