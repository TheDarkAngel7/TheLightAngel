package Angel.PlayerList;

import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class SessionManager implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(SessionManager.class);
    private final List<Session> sessions = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public SessionManager() {
        ExecutorService service = Executors.newFixedThreadPool(1);
        service.execute(new SessionClientListener());
    }

    public Session getSessionByName(String name) throws InvalidSessionException {
        lock.lock();
        try {
            int index = 0;
            while (index < sessions.size()) {
                if (sessions.get(index).getSessionName().equalsIgnoreCase(name)) return sessions.get(index);
                else if (sessions.get(index).getSessionName().toLowerCase().contains(name)) return sessions.get(index);
                index++;
            }
            throw new InvalidSessionException(name);
        }
        finally {
            lock.unlock();
        }
    }

    public Session getSessionByChannel(TextChannel channel) throws InvalidSessionException {
        try {
            return getSessionByChannel(channel.getIdLong());
        }
        catch (InvalidSessionException e) {
            throw new InvalidSessionException(channel.getName());
        }
    }

    public Session getSessionByChannel(long channelId) throws InvalidSessionException {
        int index = 0;

        while (index < sessions.size()) {
            Session session = sessions.get(index++);

            if (session.getSessionChannel().getIdLong() == channelId) return session;
        }
        throw new InvalidSessionException(String.valueOf(channelId));
    }

    // Accessibility

    public List<Session> getAccessibleSessions(Member m) {
        List<Session> result = new ArrayList<>();
        int index = 0;

        do {
            Session currentSession = sessions.get(index++);

            if (currentSession.isSessionChannelAccessible(m)) {
                result.add(currentSession);
            }
        } while (index < sessions.size());

        return result;
    }

    public List<Session> getAccessibleSessions(long targetDiscordID) {
        Member m = getGuild().getMemberById(targetDiscordID);

        return getAccessibleSessions(m);
    }

    public void setSessionPlayers(String sessionName, List<String> players, BufferedImage playerListImage) {

        List<Player> playerList = new ArrayList<>();
        List<Long> playerListLong = new ArrayList<>();
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

        int index = 0;
        do {

            String playerString = players.get(index);


            // This is the filter for filtering out the host name from the list.
            // If the player's name scores greater than 4 compared to the host name, then it's allowed in.
            int score = levenshtein.apply(sessionName, playerString);

            if (score > 4) {
                Player playerObj = new Player(playerString);

                if (!playerListLong.contains(playerObj.getDiscordAccount().getIdLong())) {
                    playerList.add(playerObj);
                    playerListLong.add(playerObj.getDiscordAccount().getIdLong());
                    log.info("{} (ID: {}) was successfully added to the player list!", playerObj.getPlayerName(), playerObj.getDiscordAccount().getIdLong());
                }
                else {
                    log.warn("{} (ID: {}) was not added as this would be a duplicate!", playerObj.getPlayerName(), playerObj.getDiscordAccount().getIdLong());
                }
            }
            else {
                log.info("{} was not added as we believe this is the session host!", playerString);
            }
        } while (++index < players.size());

        lock.lock();
        try  {
            Session sessionInQuestion = getSessionByName(sessionName);

            // If the session has been freshly brought online then there's no restrictions on the new incoming player lists

            /*
            If the player list is 0 or very low or excessively high to a number that is not possible (greater than 30),
            these are some formulas here that will be true and it'll flag that the session likely missed a screenshot

            sessionInQuestion.getPlayerList().size() - 5 > playerList.size()
            If a scenerio arises where the player list that arrived from HostControl indicates more than 5 players
            fewer than the previous list, this will be flagged as a missed screenshot, sessions generally don't shrink in size that fast.

            These restrictions should only apply after the session becomes stable, not freshly online.
             */

            if (!sessionInQuestion.getStatus().equals(SessionStatus.FRESH_ONLINE) &&
                    (sessionInQuestion.getPlayerList().size() - 5 > playerList.size() ||
                    playerList.size() > 29)) {
                if (playerList.size() > 29) {
                    log.warn("A Player List received from {} was Filtered Out due to the list count exceeding the player limit", sessionInQuestion.getSessionName());
                }
                else {
                    log.warn("A Player List received from {} was Filtered Out, the player list I received was had {} players, and the previous player list had {} players",
                            sessionInQuestion.getSessionName(), playerList.size(), sessionInQuestion.getPlayerList().size());
                }
                // If the players list in the session object is older than 10 minutes then we clear the list and accept the new data
                if (ZonedDateTime.now().isAfter(sessionInQuestion.getLastUpdatedTime().plusMinutes(10))) {
                    log.warn("Since the player list for {} was older than 10 minutes, it has been reset with fresh data", sessionInQuestion.getSessionName());
                    sessionInQuestion.clearPlayerList();
                    sessionInQuestion.setNewPlayers(playerList, playerListImage);
                }
                // Otherwise flag
                else {
                    sessionInQuestion.missedScreenshot();
                }

            }
            // The Player List received passed the integrity check by the first if statement
            else {
                log.info("A Player List received from {} Passed the Data Integrity Check: {} -> {} Players",
                        sessionInQuestion.getSessionName(), sessionInQuestion.getPlayerList().size(), playerList.size());
                sessionInQuestion.setNewPlayers(playerList, playerListImage);
            }
        }
        catch (InvalidSessionException e) {
            index = 0;
            List<TextChannel> textChannels = getGuild().getTextChannels();

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
                        sessions.add(new Session(sessionName, textChannels.get(index), new ArrayList<>(), playerListImage));
                    }
                    else {
                        sessions.add(new Session(sessionName, textChannels.get(index), playerList, playerListImage));
                    }

                    break;
                }
                index++;
            }
        }
        finally {
            lock.unlock();
        }
    }

    public void clearAllSessionPlayers() {
        sessions.forEach(Session::clearPlayerList);
    }

    public void setSessionState(String sessionName, SessionStatus sessionStatus) throws InvalidSessionException {
        Session sessionInQuestion = getSessionByName(sessionName);

        switch (sessionStatus) {
            case RESTARTING:
                sessionInQuestion.getSessionChannel().sendMessageEmbeds(new MessageEntry().setTitle(sessionInQuestion.getSessionName() + " Restarting")
                        .setMessage("**" + sessionInQuestion.getSessionName() + " is being Restarted. You will be advised in this channel " +
                                "when it is back online**").setDesign(EmbedDesign.STOP).getEmbed()).queue();
            case OFFLINE:
                if (sessionStatus.equals(SessionStatus.OFFLINE)) {
                    sessionInQuestion.getSessionChannel().sendMessageEmbeds(new MessageEntry().setTitle(sessionInQuestion.getSessionName() + " Offline")
                            .setMessage("**" + sessionInQuestion.getSessionName() + " is Offline Temporarily. You will be advised in this channel " +
                                    "when it is back online**").setDesign(EmbedDesign.STOP).getEmbed()).queue();
                }
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().deny(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + " is Offline")
                        .submit().whenComplete((permissionOverride, throwable) -> {
                            if (throwable == null) {
                                log.info("Successfully Closed the Session Channel #{}", sessionInQuestion.getSessionName());
                            }
                            else {
                                log.error("Unable to Close the session channel #{} - Reason: {}", sessionInQuestion.getSessionChannel().getName(), throwable.getMessage());
                            }
                        });
                break;
            case FRESH_ONLINE:
                sessionInQuestion.clearPlayerList();
                sessionInQuestion.getSessionChannel().sendMessageEmbeds(new MessageEntry().setTitle(sessionInQuestion.getSessionName() + " Online")
                        .setMessage("**" + sessionInQuestion.getSessionName() + " is now back online! Hop in and start grinding!**")
                        .setDesign(EmbedDesign.SUCCESS).getEmbed()).queue();
                sessionInQuestion.getSessionChannel().getPermissionOverride(mainConfig.getMemberRole())
                        .getManager().clear(Permission.MESSAGE_SEND).reason("Received Call that " + sessionInQuestion.getSessionName() + " is Online")
                        .submit().whenComplete((permissionOverride, throwable) -> {
                            if (throwable == null) {
                                log.info("Successfully Opened the Session Channel #{}", sessionInQuestion.getSessionName());
                            }
                            else {
                                log.error("Unable to Open the session channel #{} - Reason: {}", sessionInQuestion.getSessionChannel().getName(), throwable.getMessage());
                            }
                        });

                break;
        }

        sessionInQuestion.setStatus(sessionStatus);
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public int getSessionTotalPlayerCount() {
        int index = 0;
        int tally = 0;

        do {
            Session currentSession = sessions.get(index++);

            switch (currentSession.getStatus()) {
                case ONLINE, FRESH_ONLINE, RESTART_SOON ->
                        tally += currentSession.getPlayerCount();
            }
        } while (index < sessions.size());

        return tally;
    }

    public boolean usedInSessionChannel(Message msg) {
        int index = 0;
        int sessionListSize = getSessions().size();

        if (msg.getChannelType() != ChannelType.PRIVATE && !msg.getChannelType().isThread()) {
            while (index < sessionListSize) {

                if (msg.getChannel().getIdLong() == getSessions().get(index++).getSessionChannel().getIdLong()) {
                    return true;
                }
            }
        }
        return false;
    }
}
