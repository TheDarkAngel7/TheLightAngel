package Angel.CheckIn;

import java.util.ArrayList;
import java.util.List;

// This is the Record that would get saved for each player. The constructor only gets used once per player,
// afterwards then it's addMissedCheckInResult method
class CheckInRecord {
    private final long discordID;
    private int missedCheckIns;
    // CheckInResults that this player missed
    private final List<CheckInResult> results = new ArrayList<>();
    // List to say whether each result has been pardoned or not. - True means it is pardoned, False means it isn't
    private final List<Boolean> isPardoned = new ArrayList<>();

    CheckInRecord(long discordID, CheckInResult result) {
        this.discordID = discordID;
        this.missedCheckIns = 1;
        results.add(result);
        isPardoned.add(false);
    }

    long getDiscordID() {
        return discordID;
    }
    int getMissedCheckIns() {
        return missedCheckIns;
    }

    boolean pardonResult(CheckInResult pardonedResult) {
        try {
            if (isPardoned.set(results.indexOf(pardonedResult), true)) {
                missedCheckIns--;
                return true;
            }
            else return false;
        }
        catch (IndexOutOfBoundsException ex) {
            return false;
        }
    }

    void addMissedCheckInResult(CheckInResult result) {
        results.add(result);
        isPardoned.add(false);
        missedCheckIns++;
    }

    List<CheckInResult> getResults(boolean includePardoned) {
        List<CheckInResult> gottenResults = new ArrayList<>();
        int index = 0;
        while (index < results.size()) {
            if (!includePardoned && !isPardoned.get(index)) {
                gottenResults.add(results.get(index));
            }
            else gottenResults.add(results.get(index));
            index++;
        }
        return gottenResults;
    }

    CheckInPardonList getPardonedList() {
        return new CheckInPardonList(results, isPardoned);
    }
}