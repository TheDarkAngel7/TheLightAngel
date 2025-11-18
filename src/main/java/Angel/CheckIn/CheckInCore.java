package Angel.CheckIn;

import Angel.Exceptions.InvalidSessionException;
import Angel.PlayerList.Player;
import Angel.PlayerList.Session;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class CheckInCore implements CheckInLogic {
    private final Logger log = LogManager.getLogger(CheckInCore.class);
    private final Guild guild;
    private final FileHandler fileHandler = new FileHandler();
    private Session currentSession;
    private int currentCheckInID;
    // Temporary Lists Where Check-In Data is Stored
    private Dictionary<String, List<Member>> duplicateMatches = new Hashtable<>();
    private final List<CheckInPlayer> checkInList = new ArrayList<>();
    private final List<String> unrecognizedPlayer = new ArrayList<>();
    // Permanent List for All Records
    private List<CheckInResult> results = new ArrayList<>();

    CheckInCore() {
        guild = getGuild();
        results = fileHandler.getDatabase();
    }
    void saveDatabase() {
        try {
            fileHandler.saveDatabase(results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    boolean isValidSessionName(String name) {
        try {
            Session session = sessionManager.getSession(name);
            return true;
        } catch (InvalidSessionException e) {
            return false;
        }
    }
    private void setupCheckIn(String name) {
        int index = 0;
        currentCheckInID = getNextID();
        List<Session> sessions = sessionManager.getSessions();
        do {
            if (sessions.get(index).getSessionName().equalsIgnoreCase(name)) {
                currentSession = sessions.get(index);
                break;
            }
        } while (++index < sessions.size());
    }
    void loadSessionLists(String sessionName, boolean refresh) {
        log.info("Attempting to Load Session List for " + sessionName);
        AtomicReference<Boolean> encounteredErrors = new AtomicReference<>(false);
        // runReader();
        if (refresh) {
            checkInList.clear();
            unrecognizedPlayer.clear();
            duplicateMatches = new Hashtable<>();
            sessionName = currentSession.getSessionName();
        }
        setupCheckIn(sessionName);
        if (currentSession == null || currentSession.getPlayerCount() == 0) return;
        int index = 0;
        List<Player> players = currentSession.getPlayerList();

        do {
            Player player = players.get(index);
            String playerName = player.getPlayerName();
            Member m = player.getDiscordAccount();

            CheckInPlayer p = new CheckInPlayer(checkInList.size() + 1, m.getIdLong());

            if (isTeamMember(player.getDiscordAccount())) {
                p.removeFromCheckInQueue();
                log.info(m.getEffectiveName() + " was found to be a staff member and was removed from the Check-In Queue prior to start");
            }

            if (playerCanBeCheckedIn(m)) {
                p.removeFromCheckInQueue();
                log.info(m.getEffectiveName() + "(ID: " + m.getIdLong() + ") was found in the discord server but does not posses the roles required" +
                        " in order to be checked-in.");
            }

            checkInList.add(p);

        } while (++index < players.size());
    }
    boolean playerCanBeCheckedIn(Member m) {
        if (isTeamMember(m)) return false;

        int roleIndex = 0;

        while (roleIndex < ciConfig.getRolesThatCanBeCheckedIn().size()) {
            if (m.getRoles().contains(ciConfig.getRolesThatCanBeCheckedIn().get(roleIndex++))) {
                return true;
            }
        }
        return false;
    }

    void addMemberToCheckIn(List<Member> ms) {
        ms.forEach(m -> {
            checkInList.add(new CheckInPlayer(checkInList.size() + 1, m.getIdLong()));
            log.info("Successfully Added " + m.getEffectiveName() + " to the pending Check-In (Discord ID: " + m.getIdLong() + ")");
        });
    }

    boolean removeMemberFromCheckIn(List<Member> m) {
        // No Log Entries Needed here as we are calling a method for each player in the list that already
        // uses log entries for each player ran through it
        List<Boolean> booleans = new ArrayList<>();

        m.forEach(member -> booleans.add(removeMemberFromCheckIn(member)));

        return booleans.contains(true);
    }

    // Return boolean to indicate if it was a successful removal
    boolean removeMemberFromCheckIn(Member m) {
        int index = 0;
        while (index < checkInList.size()) {
            CheckInPlayer player = checkInList.get(index++);
            if (player.getPlayerDiscordId() == m.getIdLong()) {
                if (player.isQueuedToCheckIn()) {
                    player.removeFromCheckInQueue();
                    log.info("Successfully Removed " + m.getEffectiveName() + " from the upcoming check-in");
                    return true;
                }
                else {
                    log.error("Could Not Remove " + m.getEffectiveName() + " from the upcoming check-in as they were already removed");
                    return false;
                }
            }
        }
        log.error("Could Not Remove " + m.getEffectiveName() + " from the Check-In as they do not exist in it");
        return false;
    }
    boolean removeMemberFromCheckIn(int targetID) {
        int index = 0;
        while (index < checkInList.size()) {
            AtomicReference<Member> member = new AtomicReference<>();
            if (checkInList.get(index).getId() == targetID) {
                guild.retrieveMemberById(checkInList.get(index).getPlayerDiscordId()).queue(member::set);
                if (checkInList.get(index).isQueuedToCheckIn()) {
                    checkInList.get(index).removeFromCheckInQueue();
                    log.info("Successfully Removed " + member.get().getEffectiveName() + " from the upcoming check-in");
                    return true;
                }
                else {
                    log.error("Could Not Remove " + member.get().getEffectiveName() + " from the upcoming check-in as they were already removed");
                    return false;
                }
            }
            index++;
        }
        log.error("Could Not Remove Target ID" + targetID + " from the upcoming check-in as they were not found");
        return false;
    }
    long getPlayerDiscordIDFromReaction(int targetCheckInID) {
        return checkInList.get(targetCheckInID - 1).getPlayerDiscordId();
    }
    void toggleInQueueFromReaction(int targetCheckInID) {
        CheckInPlayer p = null;

        try {
            p = checkInList.get(targetCheckInID - 1);
        }
        catch (IndexOutOfBoundsException ex) {
            log.warn("IndexOutOfBoundsException Caught - Most Likely an Invalid Reaction Was Clicked: " + ex.getMessage());
            return;
        }

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
            CheckInPlayer player = checkInList.get(index++);
            if (player.getPlayerDiscordId() == m.getIdLong() && player.checkIn(remainingTime)) {
                return true;
            }
        }
        return false;
    }

    boolean everyoneIsCheckedIn() {
        int index = 0;
        do {
            if (!checkInList.get(index).successfullyCheckedIn()) {
                return false;
            }
        } while (++index < checkInList.size());
        return true;
    }

    CheckInResult endCheckIn(boolean cancelledCheckIn) {
        CheckInResult ciResult = new CheckInResult(getCurrentCheckInID(), ZonedDateTime.now(ZoneId.of("UTC")),
                new ArrayList<>(checkInList), cancelledCheckIn);
        results.add(ciResult);
        unrecognizedPlayer.clear();
        checkInList.clear();
        duplicateMatches = new Hashtable<>();
        return ciResult;
    }

    // This is when we record players that we excused from Check-In by removing them from the queue post check-in
    boolean replaceCheckInResult(CheckInResult newResult) {

        CheckInResult oldResult = getResultByID(newResult.getId());

        if (oldResult == null) {
            return false;
        }

        else {
            int insertAt = results.indexOf(oldResult);
            results.remove(oldResult);
            results.add(insertAt, newResult);
            saveDatabase();
            log.info("Successfully Updated Check-In Result with the ID " + newResult.getId());
            return true;
        }
    }

    private int getNextID() {
        int newID;
        List<Integer> usedIDs = new ArrayList<>();
        results.forEach(record -> {
            List<Integer> idsOfThisRecord = new ArrayList<>();
            int index = 0;
            do {
                idsOfThisRecord.add(results.get(index).getId());
            } while (++index < results.size());

            idsOfThisRecord.forEach(id -> {
                if (!usedIDs.contains(id)) {
                    usedIDs.add(id);
                }
            });
        });
        do {
            newID = (int) (Math.random() * 1000000);
        } while (usedIDs.contains(newID) || newID < 100000);
        return newID;
    }

    int getCurrentCheckInID() {
        return currentCheckInID;
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
    Dictionary<CheckInResult, CheckInPlayer> getResultsByPlayer(Member m) {
        Dictionary<CheckInResult, CheckInPlayer> memberResults = new Hashtable<>();

        results.forEach(r -> {
            List<CheckInPlayer> players = r.getPlayers();
            int index = 0;
            while (index < players.size()) {
                CheckInPlayer p = players.get(index++);
                if (p.getPlayerDiscordId() == m.getIdLong()) {
                    memberResults.put(r, p);
                    break;
                }
            }
        });

        return memberResults;
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
}