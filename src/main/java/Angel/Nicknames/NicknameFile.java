package Angel.Nicknames;

import java.util.List;

public class NicknameFile {
    private final List<NicknameRequest> nicknameRequests;
    private final List<PlayerNameHistory> playerNameHistory;

    public NicknameFile(List<NicknameRequest> nicknameRequests, List<PlayerNameHistory> playerNameHistory) {
        this.nicknameRequests = nicknameRequests;
        this.playerNameHistory = playerNameHistory;
    }

    public List<NicknameRequest> getNicknameRequests() {
        return nicknameRequests;
    }

    public List<PlayerNameHistory> getPlayerNameHistory() {
        return playerNameHistory;
    }
}
