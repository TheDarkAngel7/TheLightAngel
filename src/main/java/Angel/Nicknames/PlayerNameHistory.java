package Angel.Nicknames;

import java.util.ArrayList;
import java.util.List;

class PlayerNameHistory {
    private final long discordID;
    private List<String> nameHistoryList = new ArrayList<>();

    PlayerNameHistory(long discordID) {
        this.discordID = discordID;
    }

    long getDiscordID() {
        return discordID;
    }

    List<String> getNameHistoryList() {
        return nameHistoryList;
    }

    PlayerNameHistory addNameToList(String name) {
        nameHistoryList.add(name);
        return this;
    }
}
