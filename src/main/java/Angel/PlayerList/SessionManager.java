package Angel.PlayerList;

import Angel.CommonLogic;
import Angel.Exceptions.InvalidSessionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.Normalizer;
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
            else if (sessions.get(index).getSessionName().toLowerCase().contains(name)) return sessions.get(index);
            index++;
        }
        throw new InvalidSessionException(name);
    }

    public void setSessionPlayers(String sessionName, List<String> players) throws InvalidSessionException {

        List<Player> playerList = new ArrayList<>();
        LevenshteinDistance levenshtein = new LevenshteinDistance();

        players.forEach(player -> {
            // This is the filter for filtering out the host name from the list.
            // If the player's name scores greater than 4 compared to the host name, then it's allowed in.
            int score = levenshtein.apply(sessionName, player);

            if (score > 4) playerList.add(new Player(player));
        });

        try  {
            Session sessionInQuestion = getSession(sessionName);
            int sessionIndexPosition = sessions.indexOf(sessionInQuestion);

            // If the player list is 0 or very low or excessively high to a number that is not possible (greater than 30),
            // these are some formulas here that will be true
            // and it'll flag that the session likely missed a screenshot

            if (sessionInQuestion.getPlayerList().size() - 5 > playerList.size() ||
                    playerList.size() > 30) {
                sessionInQuestion = sessionInQuestion.missedScreenshot();
            }
            else {
                sessionInQuestion = sessionInQuestion.setNewPlayers(playerList);
            }
            sessions.set(sessionIndexPosition, sessionInQuestion);
        }
        catch (InvalidSessionException e) {
            System.out.println(getGuild().getTextChannels().size());
            int index = 0;
            List<TextChannel> textChannels = getGuild().getTextChannels();

            System.out.println("SessionName: " + sessionName + " Len: " + sessionName.length());

            while (index < textChannels.size()) {

                String channelName = Normalizer.normalize(textChannels.get(index).getName(), Normalizer.Form.NFD);

                log.debug(channelName + " with ID " + textChannels.get(index).getIdLong() + " Have permission "
                        + getGuild().getSelfMember().hasPermission(textChannels.get(index), Permission.VIEW_CHANNEL));

                int channelScore = levenshtein.apply(channelName, sessionName);
                boolean iHavePermission = getGuild().getSelfMember().hasPermission(textChannels.get(index), Permission.VIEW_CHANNEL);

                log.debug("Match Score " + channelScore + " iHavePermission: " + iHavePermission);

                if (channelScore <= 4 && iHavePermission) {
                    log.info("Session Channel Successfully Determined with ID " + textChannels.get(index).getIdLong());

                    if (playerList.size() > 30) {
                        sessions.add(new Session(sessionName, textChannels.get(index), new ArrayList<>()));
                    }
                    else {
                        sessions.add(new Session(sessionName, textChannels.get(index), playerList));
                    }

                    break;
                }
                index++;
            }
        }
    }
    public void clearSessionPlayers(String name) throws InvalidSessionException {
        Session sessionInQuestion = getSession(name);

        log.info("Attempting to clear player list for " +  sessionInQuestion.getSessionName());

        sessions.set(sessions.indexOf(sessionInQuestion), sessionInQuestion.clearPlayerList());
    }

    public void setSessionState(String sessionName, SessionStatus sessionStatus) throws InvalidSessionException {
        Session sessionInQuestion = getSession(sessionName);

        switch (sessionStatus) {
            case RESTARTING:
            case OFFLINE:
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().deny(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + " is Offline")
                        .submit().whenComplete((permissionOverride, throwable) -> {
                            if (throwable == null) {
                                log.info("Successfully Closed the Session Channel #{} by denying the permission: {}", sessionInQuestion.getSessionName(), permissionOverride);
                            }
                            else {
                                log.error("Unable to Close the session channel #{} - Reason: {}", sessionInQuestion.getSessionChannel().getName(), throwable.getMessage());
                            }
                        });
                break;
            case ONLINE:
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().clear(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + " is Online")
                        .submit().whenComplete((permissionOverride, throwable) -> {
                            if (throwable == null) {
                                log.info("Successfully Opened the Session Channel #{} by resetting the permission: {}", sessionInQuestion.getSessionName(), permissionOverride);
                            }
                            else {
                                log.error("Unable to Open the session channel #{} - Reason: {}", sessionInQuestion.getSessionChannel().getName(), throwable.getMessage());
                            }
                        });

                break;
        }

        sessions.set(sessions.indexOf(sessionInQuestion), sessionInQuestion.setStatus(sessionStatus));
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public int getSessionTotalPlayerCount() {
        int index = 0;
        int tally = 0;

        do {
            tally = tally + sessions.get(index++).getPlayerCount();
        } while (index < sessions.size());

        return tally;
    }
}
