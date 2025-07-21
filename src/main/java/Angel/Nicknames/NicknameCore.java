package Angel.Nicknames;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class NicknameCore implements NickLogic {
    private final Logger log = LogManager.getLogger(NicknameCore.class);
    private final FileHandler fileHandler;
    private Guild guild = getGuild();
    private List<NicknameRequest> nicknameRequests = new ArrayList<>();

    /* Temporary Nickname Requests are lost with power,
     these nickname requests are created strictly from temporary data
     Such as if a player tries to change their own nickname with the Discord GUI,
     LA captures the name they used and saves it.

     */
    private List<NicknameRequest> tempNicknameRequests = new ArrayList<>();
    // This is the History of the Names for each discord account
    private List<PlayerNameHistory> oldNickDictionary = new ArrayList<>();

    NicknameCore() {
        fileHandler = new FileHandler();
    }

    void startup() throws IOException {
        log.info("Nickname Core Initiated...");
        try {
            nicknameRequests = fileHandler.getNameRequests();
            oldNickDictionary = fileHandler.getNameHistory();
        }
        catch (IllegalStateException ex) {
            log.warn("No Data Existed in the Nickname Arrays - Data File is Empty");
        }
    }

    void reloadConfig() {
        nickConfig.reload(fileHandler.getConfig());
    }

    private int getUniqueRequestID() {
        List<Integer> requestIDs = new ArrayList<>();
        nicknameRequests.forEach(request -> {
            requestIDs.add(request.getRequestID());
        });

        int id;
        do {
            id = (int) (Math.random() * 1000);
        } while (requestIDs.contains(id) || id < 100);

        return id;
    }

    // Temporary Nickname Request Methods

    void createNewTemporaryRequest(Member targetMember, String oldName, String newName) {
        createNewTemporaryRequest(targetMember.getIdLong(), oldName, newName);
    }

    void createNewTemporaryRequest(long targetDiscordID, String oldName, String newName) {
        tempNicknameRequests.add(new NicknameRequest(getUniqueRequestID(), targetDiscordID, oldName, newName));
    }

    boolean hasTemporaryRequest(Member targetMember) {
        return hasTemporaryRequest(targetMember.getIdLong());
    }

    boolean hasTemporaryRequest(long targetDiscordID) {
        int index = 0;

        if (tempNicknameRequests.isEmpty()) return false;

        do {
            NicknameRequest nicknameRequest = tempNicknameRequests.get(index++);

            if (nicknameRequest.getDiscordID() == targetDiscordID) {
                return true;
            }
        } while (index < tempNicknameRequests.size());

        return false;
    }

    int getIndexOfTemporaryNicknameRequest(long targetDiscordID) {
        int index = 0;

        if (tempNicknameRequests.isEmpty()) return -1;

        do {
            if (tempNicknameRequests.get(index).getDiscordID() == targetDiscordID) {
                return index;
            }
            index++;
        } while (index < tempNicknameRequests.size());

        return -1;
    }

    // Permanent Nickname Request Methods

    String submitRequestFromTemporary(Member targetMember) {
        NicknameRequest request = tempNicknameRequests.remove(getIndexOfTemporaryNicknameRequest(targetMember.getIdLong()));

        nicknameRequests.add(request);

        saveDatabase();

        return getSubmissionMessage(request);
    }

    String submitRequest(Member targetMember, String newNick) {
        int id = getUniqueRequestID();

        NicknameRequest request = new NicknameRequest(id, targetMember.getIdLong(),  targetMember.getEffectiveName(), newNick);

        nicknameRequests.add(request);

        saveDatabase();

        return getSubmissionMessage(request);
    }

    String acceptRequest(long targetDiscordID, int targetRequestID) {
        String[] nicknames = null;
        try {
            if (targetDiscordID == -1) {
                nicknames = getDataAtIndexAndRemove(getNicknameRequestIndexByID(targetRequestID));
            }
            else {
                nicknames = getDataAtIndexAndRemove(getNicknameRequestIndexByDiscordID(targetDiscordID));
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
        int index = getNicknameRequestIndexByDiscordID(targetDiscordID);
        NicknameRequest request = getNicknameRequestByDiscordID(targetDiscordID);
        if (index == -1) return null;

        nicknameRequests.remove(index);

        String result = "";
        String defaultReturn = "Successfully Withdrew Nickname Request for ?";
        defaultReturn = defaultReturn.concat(replaceNulls(request.getDiscordID(),
                        "\n\n**Request Details:**" +
                        "\nRequest ID: **" + request.getRequestID() +
                        "**\nDiscord: <@!" + request.getDiscordID() + ">" +
                        "\nOld Nickname: **" + request.getOldName() +
                        "**\nNew Nickname: **" + request.getNewName() + "**"));
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

    void replaceRequest(long targetDiscordID, NicknameRequest request) {
        nicknameRequests.set(getNicknameRequestIndexByDiscordID(targetDiscordID), request);
    }

    // Get the Nickname Request by Request ID
    NicknameRequest getNicknameRequestByID(int id) {
        int index = 0;

        if (nicknameRequests.isEmpty()) return null;

        do {
            NicknameRequest request = nicknameRequests.get(index++);
            if (request.getRequestID() == id) {
                return request;
            }
        } while (index < nicknameRequests.size());

        return null;
    }

    NicknameRequest getNicknameRequestByDiscordID(long targetDiscordID) {
        int index = 0;

        if (nicknameRequests.isEmpty()) return null;

        do {
            NicknameRequest request = nicknameRequests.get(index++);
            if (request.getDiscordID() == targetDiscordID) {
                return request;
            }
        } while (index < nicknameRequests.size());

        return null;
    }

    int getNicknameRequestIndexByID(int id) {
        int index = 0;

        if (nicknameRequests.isEmpty()) return -1;

        do {
            NicknameRequest request = nicknameRequests.get(index++);
            if (request.getRequestID() == id) {
                return index - 1;
            }
        } while (index < nicknameRequests.size());

        return -1;
    }

    int getNicknameRequestIndexByDiscordID(long id) {
        int index = 0;

        if (nicknameRequests.isEmpty()) return -1;

        do {
            NicknameRequest request = nicknameRequests.get(index++);
            if (request.getDiscordID() == id) {
                return index - 1;
            }
        } while (index < nicknameRequests.size());

        return -1;
    }

    List<NicknameRequest> getNicknameRequests() {
        return nicknameRequests;
    }

    String getRequestListAsString() {
        int index = 0;
        String result = "";
        if (nicknameRequests.isEmpty()) return null;
        while (index < nicknameRequests.size()) {
            result = result.concat(replaceNulls(nicknameRequests.get(index).getDiscordID(),
                    "Request ID: **" + nicknameRequests.get(index).getRequestID() +
                            "**\nRequest Time: **" + getDiscordTimeTag(nicknameRequests.get(index).getRequestTime()) +
                            "**\nDiscord Account: <@!" + nicknameRequests.get(index).getDiscordID() + ">" +
                            "\nOld Nickname: **" + nicknameRequests.get(index).getOldName() +
                            "**\nNew Nickname: **" + nicknameRequests.get(index).getNewName() + "**"));
            if (++index <= nicknameRequests.size() - 1) result = result.concat("\n\n");
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
        NicknameRequest request = nicknameRequests.remove(index);
        String returnValue = request.getRequestID() + "," + request.getDiscordID() + ","
                + request.getOldName() + "," + request.getNewName();
        return returnValue.split(",");
    }
    List<String> getHistory(long targetDiscordID) {
        int index = 0;
        do {
            PlayerNameHistory currentRecord = oldNickDictionary.get(index++);
            if (targetDiscordID == currentRecord.getDiscordID()) {
                return currentRecord.getNameHistoryList();
            }
        } while (index < oldNickDictionary.size());

        return null;
    }
    private int getIndexInHistoryDatabase(long targetDiscordID) {
        int index = 0;
        do {
            PlayerNameHistory currentRecord = oldNickDictionary.get(index);
            if (targetDiscordID == currentRecord.getDiscordID()) {
                return index;
            }
            index++;
        } while (index < oldNickDictionary.size());

        return -1;
    }

    PlayerNameHistory getPlayerNameHistoryObject(long targetDiscordID) {
        int index = 0;
        do {
            PlayerNameHistory currentRecord = oldNickDictionary.get(index++);
            if (targetDiscordID == currentRecord.getDiscordID()) {
                return currentRecord;
            }
        } while (index < oldNickDictionary.size());

        return null;
    }

    void sendNewPlayerHistory(PlayerNameHistory history) {
        try {
            oldNickDictionary.set(getIndexInHistoryDatabase(history.getDiscordID()), history);
        }
        catch (IndexOutOfBoundsException ex) {
            oldNickDictionary.add(history);
        }
        saveDatabase();
    }

    private String getSubmissionMessage(NicknameRequest request) {

        Member targetMember = fetchMember(request.getDiscordID());

        int id = request.getRequestID();

        String newNick = request.getNewName();

        String result =
                "**:white_check_mark: New Nickname Change Request Received**" +
                        "\n\nID: **" + id +
                        "**\nDiscord: " + targetMember.getAsMention();

        if (targetMember.getNickname() != null && newNick != null) {
            return result.concat(
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
                    return result.replace("**" + newNick + "**", "**" + newNick + " (Discord Username)");
                }
                else return result;
            }

            else {
                return result.concat(
                        "\nOld Nickname: **" + targetMember.getEffectiveName() + " (Discord Username)**" +
                                "\nNew Nickname: **" + newNick + "**"
                );
            }
        }
        else if (targetMember.getNickname() != null && newNick == null) {

            if (targetMember.getUser().getGlobalName() != null) {
                return result.concat(
                        "\nOld Nickname: **" + targetMember.getNickname() +
                                "**\nNew Nickname: **" + targetMember.getUser().getGlobalName() + " (Reset to Global Display Name)**"
                );
            }

            else {
                return result.concat(
                        "\nOld Nickname: **" + targetMember.getNickname() +
                                "**\nNew Nickname: **" + targetMember.getUser().getName() + " (Reset to Discord Username)**"
                );
            }
        }
        else {
            nicknameRequests.remove(getNicknameRequestIndexByID(id));
            return "None";
        }
    }

    String clearNameHistory(long targetDiscordID) throws IOException {
        String defaultReturn = "**Successfully Cleared The Name History of <@" + targetDiscordID + ">**";
        oldNickDictionary.remove(getIndexInHistoryDatabase(targetDiscordID));
        log.info("Successfully Cleared the Name History for " + guild.getMemberById(targetDiscordID).getEffectiveName());
        saveDatabase();
        return defaultReturn;
    }
    void saveDatabase() {
        fileHandler.saveDatabase(nicknameRequests, oldNickDictionary);
    }
}