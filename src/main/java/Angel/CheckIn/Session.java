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
        private boolean isStaff = false;


        Players(String name, List<String> crew, boolean isStaff) {
            this.name = name;
            this.crew = crew;
            this.isStaff = isStaff;
        }

        String getPlayerName() {
            return name;
        }
        boolean isSAFE() {
            return crew.contains("SAFE");
        }
    }
}
