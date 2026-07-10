package Angel.PlayerList;

import Angel.EmbedDesign;
import Angel.MessageEntry;
import Angel.PlayerList.Cooldown.SessionCooldownConfiguration;
import Angel.PlayerList.Exceptions.InvalidHelpRequestException;
import Angel.PlayerList.Exceptions.KickvoteException;
import Angel.PlayerList.Exceptions.NoSessionChannelFoundException;
import Angel.PlayerList.HelpRequests.HelpRequest;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final SessionCooldownConfiguration sessionCooldownConfig;

    // Kickvote Data

    private boolean kickvoteRunning = false;
    private MessageEntry kickvoteEmbed;
    private String targetKickvotePlayer;

    private final CustomEmoji kickEmoji = getGuild().getEmojisByName("kick", true).getFirst();
    private final CustomEmoji cooldownEmoji = getGuild().getEmojisByName("cooldown", true).getFirst();
    private final String kickvoteEmbedMessage = kickEmoji.getAsMention() + " **?**" +
            "\n\n**During a kickvote, all session chatter is slowed until the kickvote is complete!**" +
            "\n\n**Vote to kick this player out with: `Pause Menu` ➡️ `Online` ➡️ `Players` ➡️ `?` ➡️ `Kick`**" +
            "\n\nReact to this message with: " +
            "\n" + kickEmoji.getAsMention() + " **to indicate you have voted to kick**" +
            "\n" + cooldownEmoji.getAsMention() + " **to indicate the Kick button is Disabled but you will check again later.**" +
            "\n ✅ **to indicate the player has left the session.**" +
            "\n ❌ **to cancel the kickvote.**";

    private int numOfBumps = 0;
    private Map<Long, CustomEmoji> kickvoteReactions;

    // This constructor is used when the bot receives information from the host, so the Session object is loaded automatically
    public Session(String name, List<Player> players, BufferedImage playerListImage, SessionCooldownConfiguration sessionCooldownConfig) throws NoSessionChannelFoundException {
        this.sessionName = name;

        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>(players);
        this.playerListImage = playerListImage;
        this.status = SessionStatus.ONLINE;

        this.sessionCooldownConfig = sessionCooldownConfig;

        resetSlowmode();
        resetPermissions();

        log.info("Successfully Created Session Object from receiving player list for {} with session channel as #{}", sessionName, sessionChannel.getName());
    }

    // This constructor is used to preload a Session object into memory, it's created with no player list or image
    public Session(String sessionName, SessionCooldownConfiguration sessionCooldownConfig) throws NoSessionChannelFoundException {
        this.sessionName = sessionName;

        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>();
        this.playerListImage = null;
        this.status = SessionStatus.OFFLINE;

        this.sessionCooldownConfig = sessionCooldownConfig;

        resetSlowmode();
        resetPermissions();

        log.info("Successfully Created Session Object from preload command for {} with session channel as #{}", sessionName, sessionChannel.getName());
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
                log.debug("{}'s Session Channel Successfully Determined with ID {}", sessionName, channels.get(index).getIdLong());

                return channels.get(index);
            }
            index++;
        }

        throw new NoSessionChannelFoundException(sessionName);
    }

    // Reset Cooldown and Permissions based on the states of different objects

    public void resetChannelParameters() {
        resetSlowmode();
        resetPermissions();
    }

    private void resetSlowmode() {
        if (!kickvoteInProgress() && sessionChannel.getSlowmode() != 0) {
            sessionChannel.getManager().setSlowmode(0).queue(
                    success -> log.info("{}'s Session Channel Slowmode Reset on the resetCooldown() method", sessionName),
                    error -> log.error("Unable to change the cooldown on {}'s session channel. MANAGE_CHANNEL Permission: {}", sessionName, sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL) ? "GRANTED" : "REQUIRED", error)
            );
        }
        else if (kickvoteInProgress() && sessionChannel.getSlowmode() == 0) {
            updateKickvoteMessage();
        }
    }

    private void resetPermissions() {
        PermissionOverride override = sessionChannel.getPermissionContainer().getPermissionOverride(mainConfig.getMemberRole());
        if (override != null) {
            if (kickvoteInProgress()) {
                if (!override.getDenied().contains(Permission.MESSAGE_EMBED_LINKS) || !override.getDenied().contains(Permission.MESSAGE_ATTACH_FILES)) {
                    sessionChannel.getPermissionOverride(mainConfig.getMemberRole())
                            .getManager()
                            .deny(Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES)
                            .queue(
                                    success -> log.info("{}'s Session Channel Permissions MESSAGE_EMBED_LINKS and MESSAGE_ATTACH_FILES were denied on the resetPermissions() method", sessionName),
                                    error -> log.error("Unable to change permissions for {}'s session channel. MANAGE_PERMISSIONS Permission: {}", sessionName, sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS), error)
                            );
                }
            }
            else {
                if (override.getDenied().contains(Permission.MESSAGE_EMBED_LINKS) || override.getDenied().contains(Permission.MESSAGE_ATTACH_FILES)) {
                    sessionChannel.getPermissionOverride(mainConfig.getMemberRole())
                            .getManager()
                            .clear(Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES)
                            .queue(
                                    success -> log.info("{}'s Session Channel Permissions MESSAGE_EMBED_LINKS and MESSAGE_ATTACH_FILES were cleared on the resetPermissions() method", sessionName),
                                    error ->  log.error("Unable to change permissions for {}'s session channel. MANAGE_PERMISSIONS Permission: {}", sessionName, sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS), error)
                            );
                }
            }
            if (status.equals(SessionStatus.ONLINE) && override.getDenied().contains(Permission.MESSAGE_SEND)) {
                override.getManager().clear(Permission.MESSAGE_SEND).queue(
                        success -> log.info("{}'s Session Channel Permission MESSAGE_SEND were cleared on the resetPermissions() method", sessionName),
                        error ->  log.error("Unable to change permissions for {}'s session channel. MANAGE_PERMISSIONS Permission: {}", sessionName, sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS), error)
                );
            }
            else if (status.equals(SessionStatus.OFFLINE) && !override.getDenied().contains(Permission.MESSAGE_SEND)) {
                override.getManager().deny(Permission.MESSAGE_SEND)
                        .queue(
                                success -> log.warn("{}'s Session Channel Permission MESSAGE_SEND was denied on the resetPermissions() method", sessionName),
                                error -> log.error("Unable to change permissions for {}'s session channel. MANAGE_PERMISSIONS Permission: {}", sessionName, sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS), error)
                        );
            }
        }
    }

    // Setters

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
                                    "Since you have not received all of your helpers, this channel has been locked.**", EmbedDesign.WARNING).getEmbed(false)).queue();
                            closeHelpRequest(hr.getHost(), "Session Pending Restart");
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
                        channel.sendMessage(":x: **The Session has gone offline, please wrap up your sales as soon as possible.**").submit().thenRun(() -> channel.leave().queue());
                    }
                });
                helpRequests.clear();
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

        if (cmd.getChannel().getIdLong() == sessionChannel.getIdLong()) {
            // If a kickvote is in progress, ONLY team members can reset the timer
            if (kickvoteInProgress()) {
                if (isTeamMember(cmd.getAuthor().getIdLong())) {
                    log.info("Cooldown Timer Reset: Team Member Used Command During Kickvote");
                    sessionCooldownConfig.setCmdLastUsed(ZonedDateTime.now());
                    sessionManager.getCooldownManager().saveConfiguration();
                }
            }
            // If NO kickvote is in progress, use our standard cooldown logic
            // Is the Cooldown inactive and the timer needs to start
            // If the Cooldown is active and a team member used the cmd
            else {
                if ((!isCooldownActive() || isTeamMember(cmd.getAuthor().getIdLong()))) {
                    log.info("Cooldown Timer Reset: No Kickvote Active, Original Cooldown {} and the command user {} a team member",
                            (isCooldownActive() ? "was active" : "was not active"), (isTeamMember(cmd.getAuthor().getIdLong())) ? "was" : "was not");
                    sessionCooldownConfig.setCmdLastUsed(ZonedDateTime.now());
                    sessionManager.getCooldownManager().saveConfiguration();
                }
            }
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

    // Kickvote Methods

    public boolean kickvoteInProgress() {
        return kickvoteRunning;
    }

    public void kickvoteWasBumped() {
        numOfBumps += 1;
        log.debug("Kickvote Was Bumped, Number of Bumps: {}", numOfBumps);
    }

    public Message getKickvoteEmbed() throws KickvoteException {
        if (!kickvoteRunning) throw new KickvoteException("Fetch Kickvote Embed with No Kickvote Running", sessionName);
        return kickvoteEmbed.getResultEmbed();
    }

    public void postReactions(long targetDiscordID, CustomEmoji emoji) throws KickvoteException {
        if (!kickvoteRunning) throw new KickvoteException("Kickvote Reaction Added But Not Running", sessionName);

        kickvoteReactions.put(targetDiscordID, emoji);

        Member m = getGuild().getMemberById(targetDiscordID);

        if (m != null) {
            log.debug("{} added reaction: {}", m.getEffectiveName(), emoji.getName());
        }
    }

    public void removeReactions(long targetDiscordID) throws KickvoteException {
        if (!kickvoteRunning) throw new KickvoteException("Kickvote Reaction Removed But Not Running", sessionName);

        CustomEmoji reaction = kickvoteReactions.remove(targetDiscordID);

        Member m = getGuild().getMemberById(targetDiscordID);

        if (m != null) {
            log.debug("{} removed reaction: {}", m.getEffectiveName(), reaction.getName());
        }
    }

    private void postKickvoteMessage(boolean repost) {

        if (repost) {
            kickvoteEmbed.getResultEmbed().delete().queueAfter(10, TimeUnit.SECONDS);
        }

        sessionChannel.sendMessageEmbeds(kickvoteEmbed.getEmbed()).queue(m -> {
            kickvoteEmbed = kickvoteEmbed.setResultEmbed(m);

            m.addReaction(kickEmoji).queue();
            m.addReaction(cooldownEmoji).queue();
            log.info("Kickvote in {}'s Session Kickvote Message was {} Successfully", sessionName,  repost ? "Reposted" : "Posted");
        }, error -> log.error("Unable to {} the Kickvote Message in {}'s Session Channel", repost ? "Reposted" : "Posted", sessionName, error));
    }

    public void initiateKickvote(Member cmdUser, String targetPlayer) throws KickvoteException {
        if (kickvoteRunning) throw new KickvoteException("Kickvote Already Running", sessionName);

        this.targetKickvotePlayer = targetPlayer;

        String title = "A Kickvote Has Been Initiated!";

        // Slow the Channel Down and Disable Message Attachments and Embeds
        sessionChannel.getManager().setSlowmode(60)
                .flatMap(voidResult -> sessionChannel.getPermissionOverride(mainConfig.getMemberRole())
                        .getManager()
                        .deny(Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS))
                .queue(
                        success -> log.info("{}'s Session Channel Cooldown has been set to 1 minute, file attachments and embeds have been disabled", sessionName),
                        error -> log.error("Unable to Change {}'s Session Channel Parameters and initialization has been halted! MANAGE_CHANNEL Permission: {}, MANAGE_PERMISSIONS Permission: {}", sessionName,
                                sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL) ? "GRANTED" : "REQUIRED",
                                sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS) ? "GRANTED" : "REQUIRED",
                                error)
                );

        kickvoteEmbed = new MessageEntry(title, kickvoteEmbedMessage.replace("?", targetKickvotePlayer), EmbedDesign.WARNING).dontUseFieldHeader();

        kickvoteRunning = true;

        kickvoteReactions = new HashMap<>();

        postKickvoteMessage(false);

        numOfBumps = 0;

        log.info("A Kickvote Has Been Initiated Against {} by {} in {}", targetKickvotePlayer, cmdUser.getEffectiveName(), sessionName);
    }

    public void updateKickvoteMessage() throws KickvoteException {
        if (!kickvoteRunning) throw new KickvoteException("No Kickvote Running", sessionName);

        int totalPlayersCountingHost = getPlayerCount() + 1;

        int numberOfVotesNeeded = (totalPlayersCountingHost / 2) + 1;

        int currentKickVotes = Math.toIntExact(kickvoteReactions.values().stream()
                        .filter(Objects::nonNull)
                        .filter(emoji -> emoji.getName().equalsIgnoreCase("kick")).count());

        boolean kickvoteThresholdMet = currentKickVotes >= numberOfVotesNeeded;

        log.debug("Kickvote Message Updating - totalPlayersCountingHost: {} - Votes: {}/{}", totalPlayersCountingHost, currentKickVotes, numberOfVotesNeeded);

        if (kickvoteThresholdMet && sessionChannel.getSlowmode() != 30) {
            sessionChannel.getManager().setSlowmode(30)
                    .queue(
                            success -> log.info("{}'s Session Channel Cooldown has been set to 30 seconds successfully",  sessionName),
                            error -> log.error("{}'s Session Channel Cooldown could not be set to 30 seconds. MANAGE_CHANNEL Permission: {}",
                                    sessionName,
                                    sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL) ? "GRANTED" : "REQUIRED",
                                    error)
                    );
        }
        if (!kickvoteThresholdMet && sessionChannel.getSlowmode() != 60) {
            sessionChannel.getManager().setSlowmode(60)
                    .queue(
                    success -> log.info("{}'s Session Channel Cooldown has been set to 30 seconds successfully" +
                            " after detecting the kickvote threshold is no longer met",  sessionName),
                    error -> log.error("{}'s Session Channel Cooldown could not be set to 1 minute. MANAGE_CHANNEL Permission: {}",
                            sessionName,
                            sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL) ? "GRANTED" : "REQUIRED",
                            error)
            );
        }

        kickvoteEmbed = kickvoteEmbed.setMessage(kickvoteEmbedMessage.replace("?", targetKickvotePlayer).concat(
                "\n\nPlayers: **" + getPlayerCount() + "**" +
                    "\nVotes: **" + currentKickVotes + "/" + numberOfVotesNeeded + "**" +
                        (kickvoteThresholdMet ? "\n\n**Threshold " + (currentKickVotes > numberOfVotesNeeded ? "Crossed" : "Met") + "!**" +
                                                                   "\n**Don't Forget to React with ✅ when you see the leave message!**" +
                                                                    "\n**If you haven't voted yet, please do so! If you have, thanks!**" : "")
        ));

        if (numOfBumps >= 3) {
            postKickvoteMessage(true);
            numOfBumps = 0;
        }
        else {
            kickvoteEmbed.getResultEmbed().editMessageEmbeds(kickvoteEmbed.getEmbed()).queue(
                    success -> log.info("Successfully Edited Kickvote Message in {}'s Session Channel. Players: {}, Votes {}/{}", sessionName, totalPlayersCountingHost, currentKickVotes, numberOfVotesNeeded),
                    error -> log.error("Unable to Edit the Kickvote Message in {}'s Session Channel", sessionName, error)
            );
        }
    }

    public void completeKickvote(Member reactionUser) throws KickvoteException {
        if (kickvoteRunning) kickvoteRunning = false;

        else throw new KickvoteException("Completed While Not Running", sessionName);

        kickvoteEmbed = kickvoteEmbed.setTitle("Kickvote Completed").setMessage(kickEmoji.getAsMention() + "**" + targetKickvotePlayer + "**" +
                "\n\n**The Kickvote has been marked as complete by " + reactionUser.getAsMention() + "**" +
                "\n\n**All Normal Session Chatter may resume at this time!**").setDesign(EmbedDesign.SUCCESS);

        kickvoteEmbed.getResultEmbed().editMessageEmbeds(kickvoteEmbed.getEmbed()).queue();

        log.info("{}'s Kickvote Has Been Marked as Complete by {}", targetKickvotePlayer, reactionUser.getEffectiveName());

        sendKickvoteResults();
    }

    public void cancelKickvote(Member reactionUser) throws KickvoteException {
        if (kickvoteRunning) kickvoteRunning = false;

        else throw new KickvoteException("Cancelled While Not Running", sessionName);

        kickvoteEmbed = kickvoteEmbed.setTitle("Kickvote Cancelled").setMessage(kickEmoji.getAsMention() + "**" + targetKickvotePlayer + "**" +
                "\n\n**The Kickvote has been cancelled by " + reactionUser.getAsMention() + "**" +
                "\n\n**Please Follow Staff Directives if any arise!**");

        kickvoteEmbed.getResultEmbed().editMessageEmbeds(kickvoteEmbed.getEmbed()).queue();

        log.info("{}'s Kickvote Has Been Marked as Cancelled by {}", targetKickvotePlayer, reactionUser.getEffectiveName());

        sendKickvoteResults();
    }

    private void sendKickvoteResults() {
        // Turn Off the Cooldown, Re-Enable Attachments and Embeds before transmitting the results
        sessionChannel.getManager().setSlowmode(0)

                .flatMap(voidResult -> sessionChannel.getPermissionOverride(mainConfig.getMemberRole())
                        .getManager()
                        .clear(Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS))
                .queue(
                        success -> log.info("{}'s Session Channel Cooldown has been disabled, file attachments and embeds have been enabled", sessionName),
                        error -> log.error("Unable to release Slowmode and Permission Restrictions on {}'s Session Channel. MANAGE_CHANNEL Permission: {}, MANAGE_PERMISSIONS Permission {}",
                                sessionName,
                                sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_CHANNEL) ? "GRANTED" : "REQUIRED",
                                sessionChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS) ? "GRANTED" : "REQUIRED",
                                error)
                );

        MessageEntry kickvoteResults = new MessageEntry();

        List<Long> votedForKick = kickvoteReactions.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equalsIgnoreCase("kick"))
                .map(Map.Entry::getKey)
                .toList();
        List<Long> votedForCooldown = kickvoteReactions.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equalsIgnoreCase("cooldown"))
                .map(Map.Entry::getKey)
                .toList();

        String result = kickEmoji.getAsMention() + " **" + targetKickvotePlayer + "**" +
                "\n\nReacted with " + kickEmoji.getAsMention() + ": " + votedForKick.stream()
                .map(id -> "<@" + id + ">")
                .collect(Collectors.joining(", ")) +

                "\n\nReacted with " + cooldownEmoji.getAsMention() + ": " + votedForCooldown.stream()
                .map(id -> "<@" + id + ">")
                .collect(Collectors.joining(", "));

        kickvoteResults = kickvoteResults.setTitle("Kickvote Results").setMessage(result).setDesign(EmbedDesign.INFO);

        mainConfig.discussionChannel.sendMessageEmbeds(kickvoteResults.getEmbed(false)).queue();

        log.info("Successfully Transmitted Kickvote Results with {} Players who voted to kick and {} Players who voted cooldown",
                votedForKick.size(), votedForCooldown.size());
    }

    // These Methods are related to the cooldown

    public void enablePlayerListCooldown(int cooldownDuration, int minNumberOfPlayers) {
        sessionCooldownConfig.setCooldownDuration(cooldownDuration);
        sessionCooldownConfig.setMinNumberOfPlayers(minNumberOfPlayers);
        enablePlayerListCooldown();
    }

    public void enablePlayerListCooldown(int cooldownDuration) {
        sessionCooldownConfig.setCooldownDuration(cooldownDuration);
        sessionCooldownConfig.setMinNumberOfPlayers(0);
        enablePlayerListCooldown();
    }

    private void enablePlayerListCooldown() {
        int minNumberOfPlayers = sessionCooldownConfig.getMinNumberOfPlayers();
        int cooldownDuration = sessionCooldownConfig.getCooldownDuration();
        ZonedDateTime cmdLastUsed = sessionCooldownConfig.getCmdLastUsed();

        // This is so when the cooldown is enabled it will immediately go active, but if cmdLastUsed is null.
        // We wait for the next time to start enforcing the cooldown
        if (cmdLastUsed == null) {
            sessionCooldownConfig.setCmdLastUsed(ZonedDateTime.now().minusMinutes(1));
        }

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been enabled for this channel.**" +
                "\n\nMinimum Duration Between Commands: **" + cooldownDuration + " Minutes**" +
                (minNumberOfPlayers > 0 ? "\nMinimum Number Of Players: **" + minNumberOfPlayers + "**" : "")).queue();
        log.info("Cooldown has been enabled for #{} - Duration: {} minutes{}", sessionChannel.getName(), cooldownDuration, (minNumberOfPlayers > 0 ? " with " + minNumberOfPlayers + " Players Minimum" : ""));
    }

    public void disablePlayerListCooldown() {
        sessionCooldownConfig.setCooldownDuration(0);

        sessionChannel.sendMessage("**`" + mainConfig.commandPrefix + "pl` Cooldown has been disabled for this channel.**").queue();
        log.info("Cooldown has been disabled for #{}", sessionChannel.getName());
    }

    public boolean isCooldownActive() {
        int minNumberOfPlayers = sessionCooldownConfig.getMinNumberOfPlayers();
        int cooldownDuration = sessionCooldownConfig.getCooldownDuration();
        ZonedDateTime cmdLastUsed = sessionCooldownConfig.getCmdLastUsed();

        // This answers whether the cooldown in the session channel is currently active
        // Is the Cooldown Setting Enabled
        // Is the current time before the time when the cooldown is over
        // Is the session over the minimum number of players to enforce the cooldown
        return isCooldownEnabled() && getPlayerCount() >= minNumberOfPlayers &&
                ZonedDateTime.now().isBefore(cmdLastUsed.plusMinutes(cooldownDuration));
    }

    public boolean isCooldownEnabled() {
        return sessionCooldownConfig.getCooldownDuration() > 0;
    }

    public String getTimerUntilCooldownIsOver() {
        int cooldownDuration = sessionCooldownConfig.getCooldownDuration();
        ZonedDateTime cmdLastUsed = sessionCooldownConfig.getCmdLastUsed();

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
    public SessionCooldownConfiguration getSessionCooldownConfiguration() {
        return sessionCooldownConfig;
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
        if (request == null) return;
        if (helpRequests.remove(request)) {
            if (!silentClose) {
                request.getThreadChannel().sendMessage("The Thread Channel has been locked and archived, Reason: **" + reason + "**").submit().thenRun(() -> {
                    request.getThreadChannel().getManager().setLocked(true).setArchived(true).and(request.getThreadChannel().leave()).queue();
                });
            }
            else {
                request.getThreadChannel().getManager().setLocked(true).setArchived(true).and(request.getThreadChannel().leave()).queue();
            }
            log.info("{}'s thread channel with the help request of \"{}\" has been closed and locked with the reason: {}",
                    request.getHost().getEffectiveName(), request.getRequest(), reason);
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
        while (index < helpRequests.size()) {
            if (helpRequests.get(index).isWaitingForHelpers()) {
                saleQueue.add(helpRequests.get(index));
            }
            index++;
        }

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

        while (index < helpRequests.size()){
            if (helpRequests.get(index).getHost().getIdLong() == discordID) {
                helpRequest = helpRequests.get(index);
                break;
            }
            index++;
        }

        return helpRequest;
    }

    public HelpRequest getHelpRequestByPlayer(Member m) {
        return getHelpRequestByPlayer(m.getIdLong());
    }

    public HelpRequest getHelpRequestByPlayer(long discordID) {
        int index = 0;

        HelpRequest helpRequest = null;

        while (index < helpRequests.size()) {
            List<Long> helpersDiscordIDs = helpRequests.get(index).getHelpers().stream().map(Member::getIdLong).toList();
            if (helpersDiscordIDs.contains(discordID)) {
                helpRequest = helpRequests.get(index);
                break;
            }
            index++;
        }

        return helpRequest;
    }

    public HelpRequest getHelpRequestByThreadChannel(ThreadChannel tc) {
        return getHelpRequestByThreadChannel(tc.getIdLong());
    }

    public HelpRequest getHelpRequestByThreadChannel(long channelID) {
        int index = 0;

        HelpRequest helpRequest = null;

        while (index < helpRequests.size()) {
            if (helpRequests.get(index).getThreadChannel().getIdLong() == channelID) {
                helpRequest = helpRequests.get(index);
                break;
            }
            index++;
        }

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
