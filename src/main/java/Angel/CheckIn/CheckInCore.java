package Angel.CheckIn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;

class CheckInCore {
    private final Logger log = LogManager.getLogger(CheckInCore.class);
    private final CheckInConfiguration ciConfig;
    private final Guild guild;
    private List<Session> sessions;
    private int currentCheckInID;
    // Temporary Lists Where Check-In Data is Stored
    private Dictionary<String, List<Member>> duplicateMatches = new Hashtable<>();
    private final List<CheckInPlayer> checkInList = new ArrayList<>();
    private final List<String> unrecognizedPlayer = new ArrayList<>();
    // Permanent List for All Records
    private List<CheckInRecord> records = new ArrayList<>();
    private List<CheckInResult> results = new ArrayList<>();

    CheckInCore(CheckInConfiguration ciConfig, Guild guild) {
        this.ciConfig = ciConfig;
        this.guild = guild;
    }

    void setRecords(List<CheckInRecord> records) {
        this.records = records;
    }

    void setResults(List<CheckInResult> results) {
        this.results = results;
    }

    boolean isValidSessionName(String name) {
        runReader();
        int index = 0;

        while (index < sessions.size()) {
            if (sessions.get(index).getSessionName().equalsIgnoreCase(name)) return true;
            else if (sessions.get(index).getSessionName().toLowerCase().contains(name.toLowerCase())) return true;
            index++;
        }
        return false;
    }
    void setupCheckIn(String name) {
        Session session = null;
        runReader();
        int index = 0;
        currentCheckInID = getNextID();
        do {
            if (sessions.get(index).getSessionName().equalsIgnoreCase(name)) {
                session = sessions.get(index);
                break;
            }
        } while (index < sessions.size());

        if (session == null || session.getPlayerCount() == 0) return;

        index = 0;
        List<Session.Players> players = session.getPlayerList();

        while (index < players.size()) {
            String playerName = players.get(index++).getPlayerName();
            if (players.get(index).isSAFE()) {
                List<Member> ms = guild.getMembersByEffectiveName(playerName, true);
                if (ms.size() == 1) checkInList.add(new CheckInPlayer(checkInList.size() + 1, ms.get(0)));
                else if (ms.size() > 1) {
                    List<Member> membersFoundByRole = new ArrayList<>();
                    int searchIndex = 0;
                    int roleIndex = 0;
                    while (searchIndex < ms.size()) {
                        while (roleIndex < ciConfig.getRolesThatCanBeCheckedIn().size()) {
                            if (ms.get(searchIndex).getRoles().contains(ciConfig.getRolesThatCanBeCheckedIn().get(roleIndex++))) {
                                membersFoundByRole.add(ms.get(searchIndex));
                            }
                        }
                        searchIndex++;
                    }
                    if (membersFoundByRole.size() == 1) {
                        checkInList.add(new CheckInPlayer(checkInList.size() + 1, membersFoundByRole.get(0)));
                    }
                    else if (membersFoundByRole.isEmpty()) {
                        unrecognizedPlayer.add(playerName);
                    }
                    else {
                        duplicateMatches.put(playerName, ms);
                    }
                }
                else {
                    unrecognizedPlayer.add(playerName);
                }
            }
            else {
                unrecognizedPlayer.add(playerName);
            }
        }
    }
    void addMemberToCheckIn(List<Member> ms) {
        ms.forEach(m -> checkInList.add(new CheckInPlayer(checkInList.size() + 1, m)));
    }
    // Return Boolean to indicate whether or not it was successful
    boolean addMemberFromDuplicateQuery(Member m) {
        Enumeration<String> originalQuery = duplicateMatches.keys();
        Enumeration<List<Member>> membersFromQuery = duplicateMatches.elements();
        do {
            String query = originalQuery.nextElement();
            List<Member> currentList = membersFromQuery.nextElement();
            if (currentList.contains(m)) {
                checkInList.add(new CheckInPlayer(checkInList.size() + 1, m));
                duplicateMatches.remove(query);
                return true;
            }
        } while (duplicateMatches.elements().hasMoreElements());
        return false;
    }
    // Return boolean to indicate if it was a successful removal
    boolean removeMemberFromCheckIn(Member m) {
        int index = 0;
        while (index < checkInList.size()) {
            if (checkInList.get(index).getPlayer() == m) {
                checkInList.get(index).removeFromCheckInQueue();
                log.info("Successfully Removed " + checkInList.get(index).getPlayer().getEffectiveName() + " from the upcoming check-in");
                return true;
            }
            index++;
        }
        return false;
    }
    boolean removeMemberFromCheckIn(int targetID) {
        int index = 0;
        while (index < checkInList.size()) {
            if (checkInList.get(index).getId() == targetID) {
                if (checkInList.get(index).isQueuedToCheckIn()) {
                    checkInList.get(index).removeFromCheckInQueue();
                    log.info("Successfully Removed " + checkInList.get(index).getPlayer().getEffectiveName() + " from the upcoming check-in");
                    return true;
                }
                else {
                    log.error("Could Not Remove " + checkInList.get(index).getPlayer().getEffectiveName() + " from the upcoming check-in as they were already removed");
                    return false;
                }
            }
            index++;
        }
        log.error("Could Not Remove Target ID" + targetID + " from the upcoming check-in as they were not found");
        return false;
    }
    void toggleInQueueFromReaction(int targetCheckInID) {
        CheckInPlayer p = checkInList.get(targetCheckInID);
        if (p.isQueuedToCheckIn()) {
            p.removeFromCheckInQueue();
        }
        else {
            p.addToCheckInQueue();
        }
    }
    boolean checkInMember(User u, String remainingTime) {
        int index = 0;
        if (!guild.isMember(u)) return false;
        Member m = guild.getMember(u);
        while (index < checkInList.size()) {
            if (checkInList.get(index).getPlayer() == m && checkInList.get(index).checkIn(remainingTime)) {
                return true;
            }
            index++;
        }
        return false;
    }
    CheckInResult endCheckIn(ZonedDateTime startDate) {

        CheckInResult ciResult = new CheckInResult(currentCheckInID, startDate, checkInList);

        checkInList.forEach(p -> {
            if (!p.successfullyCheckedIn()) {
                try {
                    getRecordByDiscordID(p.getPlayer().getIdLong()).addMissedCheckInResult(ciResult);
                }
                catch (NullPointerException ex) {
                    records.add(new CheckInRecord(p.getPlayer().getIdLong(), ciResult));
                }
            }
        });
        results.add(ciResult);
        unrecognizedPlayer.clear();
        checkInList.clear();
        duplicateMatches = new Hashtable<>();
        sessions.clear();
        return ciResult;
    }

    boolean pardonMemberOnLatestResult(Member m) {
        return getRecordByMember(m).pardonResult(getLatestResult());
    }

    boolean pardonMemberByResultID(Member m, int targetResultID) {
        int index = 0;
        CheckInRecord record = getRecordByMember(m);

        List<CheckInResult> results = record.getResults(false);

        do {
            if (results.get(index).getId() == targetResultID) {
                record.pardonResult(results.get(index));
                return true;
            }
        } while (++index < results.size());

        return false;
    }

    private int getNextID() {
        int newID;
        List<Integer> usedIDs = new ArrayList<>();
        records.forEach(record -> {
            List<Integer> idsOfThisRecord = new ArrayList<>();
            int index = 0;
            List<CheckInResult> results = record.getResults(true);
            do {
                idsOfThisRecord.add(results.get(index).getId());
            } while (++index < results.size());

            if (!idsOfThisRecord.isEmpty()) {
                idsOfThisRecord.forEach(id -> {
                    if (!usedIDs.contains(id)) {
                        usedIDs.add(id);
                    }
                });
            }
        });
        do {
            newID = (int) (Math.random() * 1000000);
        } while (usedIDs.contains(newID) || newID < 100000);
        return newID;
    }

    int getCurrentCheckInID() {
        return currentCheckInID;
    }

    private void runReader() {
        sessions = new ArrayList<>();
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL("https://fingers.wtf/zoo/rsrc/sessions.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.connect();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        int index = 0;
        JsonArray array = JsonParser.parseString(response.toString()).getAsJsonArray();
        while (index < array.size()) {
            Gson gson = new Gson();
            JsonObject sessionObj = array.get(index++).getAsJsonObject();
            JsonArray playersArray = sessionObj.get("players").getAsJsonArray();
            List<Session.Players> players = new ArrayList<>();
            int playerIndex = 0;

            do {
                if (playersArray.size() == 0) break;
                JsonObject playersObj = playersArray.get(playerIndex).getAsJsonObject();
                try {
                    if (playersObj.get("rank").getAsString().equalsIgnoreCase("staff")) {
                        players.add(new Session.Players(playersObj.get("name").getAsString(),
                                gson.fromJson(playersObj.get("crew").getAsString(), new TypeToken<List<String>>(){}.getType()), true));
                    }
                }
                catch (NullPointerException ex) {
                    players.add(new Session.Players(playersObj.get("name").getAsString(),
                            gson.fromJson(playersObj.get("crew").getAsString(), new TypeToken<List<String>>(){}.getType()), false));
                }
            } while (++playerIndex < playersArray.size());

            sessions.add(new Session(
                    sessionObj.get("date").getAsString(),
                    sessionObj.get("pc").getAsString(),
                    players,
                    sessionObj.get("session").getAsString()
            ));
        }
    }
    List<CheckInResult> getAllResults() {
        return results;
    }
    CheckInResult getResultByID(int id) {
        List<CheckInResult> search = getAllResults();
        int index = 0;

        while (index < search.size()) {
            if (search.get(index).getId() == id) return search.get(index);
            index++;
        }
        return null;
    }

    CheckInResult getLatestResult() {
        return results.get(results.size() - 1);
    }

    CheckInRecord getRecordByDiscordID(long targetDiscordID) {
        int index = 0;

        do {
            if (records.get(index).getDiscordID() == targetDiscordID) return records.get(index);
        } while (++index < records.size());

        return null;
    }

    CheckInRecord getRecordByMember(Member m) {
        return getRecordByDiscordID(m.getIdLong());
    }
    List<CheckInPlayer> getCheckInList() {
        return checkInList;
    }

    Dictionary<String, List<Member>> getDuplicateMatchHashTable() {
        return duplicateMatches;
    }

    List<String> getUnrecognizedPlayerList() {
        return unrecognizedPlayer;
    }

    List<CheckInRecord> getRecords() {
        return records;
    }

    List<CheckInResult> getResults() {
        return results;
    }
}