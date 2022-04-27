package Angel.CheckIn;

import java.util.List;

class Session {
    private final String date;
    private final int pc;
    private final List<Players> players;
    private final String session;

    Session(String date, String pc, List<Players> players, String session) {
        this.date = date;
        this.pc = Integer.parseInt(pc);
        this.players = players;
        this.session = session;

    }
    int getPlayerCount() {
        return pc;
    }
    List<Players> getPlayerList() {
        return players;
    }
    String getSessionName() {
        return session;
    }

    static class Players {
        private final String name;
        private final List<String> crew;


        Players(String name, List<String> crew) {
            this.name = name;
            this.crew = crew;
        }

        String getPlayerName() {
            return name;
        }
        boolean isSAFE() {
            return crew.contains("SAFE");
        }
        boolean isStaff() {
            return crew.contains("staff");
        }
    }
}
