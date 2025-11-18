package Angel.PlayerList;

import Angel.CommonLogic;
import Angel.Exceptions.InvalidSessionException;
import net.dv8tion.jda.api.Permission;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionManager implements CommonLogic {
    private final Logger log = LogManager.getLogger(SessionManager.class);
    private final List<Session> sessions = new ArrayList<>();

    public SessionManager() {
        ExecutorService service = Executors.newFixedThreadPool(1);
        service.execute(new SessionClientListener());
    }

    public Session getSession(String name) throws InvalidSessionException {
        int index = 0;
        while (index < sessions.size()) {
            if (sessions.get(index).getSessionName().equalsIgnoreCase(name)) return sessions.get(index);
            else if (sessions.get(index).getSessionName().toLowerCase().contains(name.toLowerCase())) return sessions.get(index);
            index++;
        }
        throw new InvalidSessionException(name);
    }

    public void setSessionPlayers(String sessionName, List<String> players) throws InvalidSessionException {

        List<Players> playersList = new ArrayList<>();
        JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();

        players.forEach(player -> {
            // This is the filter for filtering out the host name from the list.
            // If the player's name scores lower than 0.9 match then it's allowed in.
            double score = jaroWinkler.apply(sessionName, player);

            if (score < 0.9) playersList.add(new Players(player));
        });

        try  {
            Session sessionInQuestion = getSession(sessionName);
            int sessionIndexPosition = sessions.indexOf(sessionInQuestion);

            // If the player list is 0 or very low, these are some formulas here that any of them could come up true
            // and it'll flag that the session likely missed a screenshot

            if (sessionInQuestion.getPlayerList().size() - 5 > playersList.size()) {
                sessionInQuestion = sessionInQuestion.missedScreenshot();
            }
            else {
                sessionInQuestion = sessionInQuestion.setNewPlayers(playersList);
            }
            sessions.set(sessionIndexPosition, sessionInQuestion);
        }
        catch (InvalidSessionException e) {
            sessions.add(new Session(sessionName, playersList));
        }
    }

    public void setSessionState(String sessionName, SessionStatus sessionStatus) throws InvalidSessionException {
        Session sessionInQuestion = getSession(sessionName);

        switch (sessionStatus) {
            case RESTARTING:
            case OFFLINE:
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().deny(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + "is Offline")
                        .submit().whenComplete(
                                (permissionOverride, throwable) ->
                                        log.info("Successfully Closed the Session Channel #" + sessionInQuestion.getSessionChannel().getName()));
                break;
            case ONLINE:
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().clear(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + "is Online")
                        .submit().whenComplete((permissionOverride, throwable) ->
                                log.info("Successfully Opened the Session Channel #" + sessionInQuestion.getSessionChannel().getName()));
                break;
        }

        sessions.set(sessions.indexOf(sessionInQuestion), sessionInQuestion.setStatus(sessionStatus));
    }

    public List<Session> getSessions() {
        return sessions;
    }
}
