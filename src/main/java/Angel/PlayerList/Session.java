package Angel.PlayerList;

import Angel.EmbedDesign;
import Angel.Exceptions.InvalidHelpRequestException;
import Angel.Exceptions.NoSessionChannelFoundException;
import Angel.MessageEntry;
import Angel.PlayerList.HelpRequests.HelpRequest;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Session implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(Session.class);

    private final String sessionName;
    private final TextChannel sessionChannel;

    private ZonedDateTime playerListLastUpdated;
    private List<Player> players;
    private BufferedImage playerListImage;

    private List<HelpRequest> helpRequests = new ArrayList<>();

    // Player List Trouble means LA received an empty player list and this session may be experiencing trouble,
    // If this happens 5 times then we'll put the session into the trouble status
    private int missedScreenshots = 0;

    private SessionStatus status;

    // Session Channel Player List Cooldown

    private boolean playerListCooldownEnabled = false;
    private ZonedDateTime cmdLastUsed = null;
    private int cooldownDuration = 0;
    private int minNumberOfPlayers = 0;

    public Session(String name, List<Player> players, BufferedImage playerListImage) throws NoSessionChannelFoundException {
        this.sessionName = name;

        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>(players);
        this.playerListImage = playerListImage;
        this.status = SessionStatus.ONLINE;
    }

    public Session(String sessionName) throws NoSessionChannelFoundException {
        this.sessionName = sessionName;
        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>();

        this.playerListImage = null;
        this.status = SessionStatus.OFFLINE;
    }

    // Find Session Channel

    private TextChannel fetchSessionChannel() throws NoSessionChannelFoundException {
        List<TextChannel> channels = getGuild().getTextChannels();

        int index = 0;
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

        while (index < channels.size()) {

            String channelName = Normalizer.normalize(channels.get(index).getName(), Normalizer.Form.NFD);

            log.debug("{} with ID {} Have permission {}", channelName, channels.get(index).getIdLong(), getGuild().getSelfMember().hasPermission(channels.get(index), Permission.VIEW_CHANNEL));

            int channelScore = levenshtein.apply(channelName, sessionName);
            boolean iHavePermission = getGuild().getSelfMember().hasPermission(channels.get(index), Permission.VIEW_CHANNEL);

            log.debug("Match Score {} iHavePermission: {}", channelScore, iHavePermission);

            if (channelScore <= 4 && iHavePermission) {
                log.info("Session Channel Successfully Determined with ID {}", channels.get(index).getIdLong());

                return channels.get(index);
            }
            index++;
        }

        throw new NoSessionChannelFoundException(sessionName);
    }

    public void setNewPlayers(List<Player> players, BufferedImage playerListImage) {
        this.players = players;
        this.playerListImage = playerListImage;
        this.playerListLastUpdated = ZonedDateTime.now();
        resetListFilter();
    }

    public void setStatus(SessionStatus status) {
        this.status = status;

        switch (status) {
            case RESTART_SOON ->
                    helpRequests.forEach(hr -> {
                        ThreadChannel channel = hr.getThreadChannel();
                        if (hr.isWaitingForHelpers()) {


                            channel.sendMessage("@everyone").queue();
                            channel.sendMessageEmbeds(new MessageEntry("Session Pending Restart", ":warning: **Heads up " + hr.getHost().getAsMention() + "! The session is going to restart soon! " +
                                    "Since you have not received all of your helpers, this channel has been locked. Please disband your CEO/MC slot if you have one and leave the session, " +
                                    "we don't want your sales to be in progress when the restart begins!**", EmbedDesign.WARNING).getEmbed(false)).queue();
                            closeHelpRequest(hr.getHost(), "Session Pending Restart");
                            channel.leave().queue();
                        }
                        else {
                            channel.sendMessage("@everyone").queue();
                            channel.sendMessageEmbeds(new MessageEntry("Session Pending Restart", ":warning: **Heads Up! " + sessionName + " is about to restart. " +
                                    "Please wrap up your sales no later than the time displayed in the " + sessionChannel.getAsMention() + " channel.", EmbedDesign.WARNING).getEmbed(false)).queue();

                        }
                    });
            case RESTARTING, RESTART_MOD, OFFLINE -> {
                helpRequests.forEach(hr -> {
                    ThreadChannel channel = hr.getThreadChannel();
                    if (hr.isWaitingForHelpers()) {
                        channel.sendMessage(":x: **The Session has gone offline while you were waiting for helpers. This thread has been closed and locked**").queue();
                        closeHelpRequest(hr.getHost(), "Session Offline");
                    }
                    else {
                        channel.sendMessage(":x: **The Session has gone offline, please wrap up your sales as soon as possible.**").queue();
                    }
                });
            }

        }
    }

    public void missedScreenshot() {
        missedScreenshots++;

        log.warn("{} has missed a screenshot. Count: {} In Trouble: {}", sessionName, missedScreenshots, isExperiencingScreenshotTrouble());
    }

    public String getSessionName() {
        return sessionName;
    }

    public TextChannel getSessionChannel() {
        return sessionChannel;
    }

    public ZonedDateTime getLastUpdatedTime() {
        return playerListLastUpdated;
    }

    public BufferedImage getPlayerListImage() {
        return playerListImage;
    }

    public String getLastUpdateTimeString() {
        return getTimerFormatFrom(playerListLastUpdated);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public List<Player> getPlayerList() {
        return players;
    }

    public PlayerListMessage getPlayerListMessage(Message cmd, boolean sortAlphabetically, boolean useMentions) {
        return getPlayerListMessage(cmd)
                .sortListAlphabetically(sortAlphabetically).useMentions(useMentions);
    }

    public PlayerListMessage getPlayerListMessage(Message cmd) {
        // We're only watching if the command is used in the session channel and two things will reset the cooldown timer:
        // Is the Cooldown inactive and the timer needs to start
        // If the Cooldown is active and a team member used the cmd
        // If either condition is true it'll still have the same effect
        if ((!isCooldownActive() || isTeamMember(cmd.getAuthor().getIdLong())) && cmd.getChannel().getIdLong() == sessionChannel.getIdLong()) {
            cmdLastUsed = ZonedDateTime.now();
        }

        return new PlayerListMessage(this);
    }

    public void clearPlayerList() {
        players.clear();
        log.info("Player List for {} has been cleared",  sessionName);
    }

    public SessionStatus getStatus() {
        return status;
    }

    public boolean isExperiencingScreenshotTrouble() {
        return missedScreenshots >= 5;
    }

    public void resetListFilter() {
        this.missedScreenshots = 0;
        log.info("List Filter has been reset for {}", sessionName);
    }
    // If the Session Channel is Accessible to the User

    public boolean isSessionChannelAccessible(Member m) {
        if (m == null) return false;

        return m.hasPermission(sessionChannel, Permission.VIEW_CHANNEL);
    }

    public boolean isSessionChannelAccessible(long targetDiscordID) {
        Member m = getGuild().getMemberById(targetDiscordID);

        return isSessionChannelAccessible(m);
    }

    // These Methods are related to the cooldown

    public void enablePlayerListCooldown(int cooldownDuration, int minNumberOfPlayers) {
        this.minNumberOfPlayers = minNumberOfPlayers;
        this.cooldownDuration = cooldownDuration;
        enablePlayerListCooldown();
    }

    public void enablePlayerListCooldown(int cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
        this.minNumberOfPlayers = 0;
        enablePlayerListCooldown();
    }

    private void enablePlayerListCooldown() {
        playerListCooldownEnabled = true;

        // This is so when the cooldown is enabled it will immediately go active, but if cmdLastUsed is null.
        // We wait for the next time to start enforcing the cooldown
        if (cmdLastUsed == null) {
            this.cmdLastUsed = ZonedDateTime.now().minusMinutes(cooldownDuration + 1);
        }

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been enabled for this channel.**" +
                "\n\nMinimum Duration Between Commands: **" + cooldownDuration + " Minutes**" +
                (minNumberOfPlayers > 0 ? "\nMinimum Number Of Players: **" + minNumberOfPlayers + "**" : "")).queue();
        log.info("Cooldown has been enabled for #{} - Duration: {} minutes with {} Players Minimum", sessionChannel.getName(), cooldownDuration, minNumberOfPlayers);
    }

    public void disablePlayerListCooldown() {
        playerListCooldownEnabled = false;

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been disabled for this channel.**").queue();
        log.info("Cooldown has been disabled for #{}", sessionChannel.getName());
    }

    public boolean isCooldownActive() {
        // This answers whether the cooldown in the session channel is currently active
        // Is the Cooldown Setting Enabled
        // Is the current time before the time when the cooldown is over
        // Is the session over the minimum number of players to enforce the cooldown
        return playerListCooldownEnabled && getPlayerCount() >= minNumberOfPlayers &&
                ZonedDateTime.now().isBefore(cmdLastUsed.plusMinutes(cooldownDuration));
    }

    public boolean isCooldownEnabled() {
        return playerListCooldownEnabled;
    }

    public String getTimerUntilCooldownIsOver() {
        return getTimerFormatTo(cmdLastUsed.plusMinutes(cooldownDuration));
    }

    public String getAbbreviationSuggestion() {
        String[] tokens = sessionName.split("(?=\\p{Upper})");
        String token = tokens[new Random().nextInt(tokens.length)];

        // Determine the target length: 2, 3, or 4
        // nextInt(3) returns 0, 1, or 2. Adding 2 makes it 2, 3, or 4.
        int targetLength = new Random().nextInt(3) + 2;

        // Adjust the length if the token is shorter than the target
        int finalLength = Math.min(token.length(), targetLength);

        // Return Substring
        return token.substring(0, finalLength).toLowerCase();
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }

    public int getMinNumberOfPlayersInSessionForCooldown() {
        return minNumberOfPlayers;
    }

    public boolean isPlayerInSession(Member m) {
        return isPlayerInSession(m.getIdLong());
    }

    public boolean isPlayerInSession(long discordID) {
        int index = 0;

        do {
            if (players.get(index++).getDiscordAccount().getIdLong() == discordID) {
                return true;
            }

        } while (index < players.size());

        return false;
    }

    // Everything Below this line has to do with the HelpRequests

    public void createNewHelpRequest(Message cmd) {
        try {
            HelpRequest helpRequest = new HelpRequest(cmd);

            helpRequests.add(helpRequest);

            sessionChannel.sendMessage("**" + helpRequest.getHost().getEffectiveName() + " is needing help with " + helpRequest.getRequest() + "**" +
                    "\n\nQueue Position: **" + getQueuePositionByHost(helpRequest.getHost()) + "**").queue();

            log.info("{} has created a help request for {} - Queue Position on Creation: {}", helpRequest.getHost().getEffectiveName(), helpRequest.getRequest(), getQueuePositionByHost(helpRequest.getHost()));
        }
        catch (InvalidHelpRequestException e) {
            cmd.getChannel().sendMessageEmbeds(new MessageEntry("Error Creating Help Request", "**Unable to Create Help Request**\n\nReason: **" + e.getMessage() + "**", EmbedDesign.ERROR)
                    .getEmbed(false)).queue(m -> {
                        m.delete().queueAfter(30, TimeUnit.SECONDS);
                        cmd.delete().queueAfter(30, TimeUnit.SECONDS);
            });

            log.error("Unable to Create Help Request - Reason: {}", e.getMessage());
        }
    }

    public void closeHelpRequest(long targetDiscordID, String reason) {
        closeHelpRequest(getHelpRequestByHost(targetDiscordID), reason, false);
    }

    public void closeHelpRequest(Member m, String reason) {
        closeHelpRequest(getHelpRequestByHost(m), reason, false);
    }

    public void closeHelpRequest(HelpRequest request, String reason, boolean silentClose) {
        if (helpRequests.remove(request)) {
            if (!silentClose) {
                request.getThreadChannel().sendMessage("The Thread Channel has been locked and archived, Reason: **" + reason + "**").queue();
            }
            log.info("{}'s thread channel with the help request of \"{}\" has been closed and locked with the reason: {}",
                    request.getHost().getEffectiveName(), request.getRequest(), reason);

            request.getThreadChannel().getManager().setLocked(true).setArchived(true).and(request.getThreadChannel().leave()).queue();
        }

        else {
            log.error("Unable to Close and Lock the thread channel for {} with reason {}", request.getHost().getEffectiveName(), reason);
        }
    }

    public List<HelpRequest> getHelpRequests() {
        return helpRequests;
    }

    public List<HelpRequest> getSaleQueue(boolean sort) {
        List<HelpRequest> saleQueue = new ArrayList<>();

        int index = 0;
        do {
            if (helpRequests.get(index).isWaitingForHelpers()) {
                saleQueue.add(helpRequests.get(index));
            }
            index++;
        } while (index < helpRequests.size());

        if (sort) {
            return saleQueue.stream().sorted(Comparator.comparing(HelpRequest::getRequestCreationTime)).toList();
        }
        else return saleQueue;
    }

    public int getSaleQueueSize() {
        return getSaleQueue(false).size();
    }

    public HelpRequest getHelpRequestByHost(Member m) {
        if (m == null) return null;

        return getHelpRequestByHost(m.getIdLong());
    }

    public HelpRequest getHelpRequestByHost(long discordID) {
        int index = 0;

        HelpRequest helpRequest = null;

        do {
            if (helpRequests.get(index).getHost().getIdLong() == discordID) {
                helpRequest = helpRequests.get(index);
                break;
            }
        } while (++index < helpRequests.size());

        return helpRequest;
    }

    public HelpRequest getHelpRequestByPlayer(Member m) {
        return getHelpRequestByPlayer(m.getIdLong());
    }

    public HelpRequest getHelpRequestByPlayer(long discordID) {
        int index = 0;

        HelpRequest helpRequest = null;

        do {
            List<Long> helpersDiscordIDs = helpRequests.get(index).getHelpers().stream().map(Member::getIdLong).toList();
            if (helpersDiscordIDs.contains(discordID)) {
                helpRequest = helpRequests.get(index);
                break;
            }
        } while (++index < helpRequests.size());

        return helpRequest;
    }

    public HelpRequest getHelpRequestByThreadChannel(ThreadChannel tc) {
        return getHelpRequestByThreadChannel(tc.getIdLong());
    }

    public HelpRequest getHelpRequestByThreadChannel(long channelID) {
        int index = 0;

        HelpRequest helpRequest = null;

        do {
            if (helpRequests.get(index).getThreadChannel().getIdLong() == channelID) {
                helpRequest = helpRequests.get(index);
                break;
            }
        } while (++index < helpRequests.size());

        return helpRequest;
    }

    public int getQueuePositionByHost(Member m) {
        return getQueuePositionByHost(m.getIdLong());
    }

    public int getQueuePositionByHost(long discordID) {
        List<HelpRequest> saleQueue = getSaleQueue(true);

        int index = 0;

        do {
            if (saleQueue.get(index).getHost().getIdLong() == discordID) {
                // We Return Index + 1 For Position as Index 0 = Position 1 in Queue and so forth
                return index + 1;
            }
        } while (++index < saleQueue.size());

        // We Return 0 if the Host's Sale is not in Queue
        return 0;
    }
}
