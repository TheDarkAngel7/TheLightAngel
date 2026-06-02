package Angel.PlayerList;

import Angel.EmbedDesign;
import Angel.MessageEntry;
import Angel.PlayerList.Exceptions.KickvoteException;
import Angel.PlayerList.Exceptions.NoSessionChannelFoundException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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

    // Player List Trouble means LA received an empty player list and this session may be experiencing trouble,
    // If this happens 5 times then we'll put the session into the trouble status
    private int missedScreenshots = 0;

    private SessionStatus status;

    // Session Channel Player List Cooldown

    private boolean playerListCooldownEnabled = false;
    private ZonedDateTime cmdLastUsed = null;
    private int cooldownDuration = 0;
    private int minNumberOfPlayers = 0;

    // Kickvote Data

    private boolean kickvoteRunning = false;
    private MessageEntry kickvoteEmbed;
    private String targetKickvotePlayer;

    private final CustomEmoji kickEmoji = getGuild().getEmojisByName("kick", true).getFirst();
    private final CustomEmoji cooldownEmoji = getGuild().getEmojisByName("cooldown", true).getFirst();
    private final String kickvoteEmbedMessage = kickEmoji.getAsMention() + "**?**" +
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
    public Session(String name, List<Player> players, BufferedImage playerListImage) throws NoSessionChannelFoundException {
        this.sessionName = name;

        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>(players);
        this.playerListImage = playerListImage;
        this.status = SessionStatus.ONLINE;

        resetCooldown();
        resetPermissions();
    }

    // This constructor is used to preload a Session object into memory, it's created with no player list or image
    public Session(String sessionName) throws NoSessionChannelFoundException {
        this.sessionName = sessionName;

        this.sessionChannel = fetchSessionChannel();

        this.playerListLastUpdated = ZonedDateTime.now();
        this.players = new ArrayList<>();
        this.playerListImage = null;
        this.status = SessionStatus.OFFLINE;

        resetCooldown();
        resetPermissions();
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
                log.info("{}'s Session Channel Successfully Determined with ID {}", sessionName, channels.get(index).getIdLong());

                return channels.get(index);
            }
            index++;
        }

        throw new NoSessionChannelFoundException(sessionName);
    }

    // Reset Cooldown and Permissions based on the states of different objects

    public void resetChannelParameters() {
        resetCooldown();
        resetPermissions();
    }

    private void resetCooldown() {
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
        log.info("{}'s Session State has been set: {}", sessionName, this.status.getStatusString());
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
                    cmdLastUsed = ZonedDateTime.now();
                }
            }
            // If NO kickvote is in progress, use our standard cooldown logic
            // Is the Cooldown inactive and the timer needs to start
            // If the Cooldown is active and a team member used the cmd
            else {
                if ((!isCooldownActive() || isTeamMember(cmd.getAuthor().getIdLong()))) {
                    log.info("Cooldown Timer Reset: No Kickvote Active, Original Cooldown {} and the command user {} a team member",
                            (isCooldownActive() ? "was active" : "was not active"), (isTeamMember(cmd.getAuthor().getIdLong())) ? "was" : "was not");
                    cmdLastUsed = ZonedDateTime.now();
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
            log.info("Kickvote in {}'s Session wasMessage {} Successfully", sessionName,  repost ? "Reposted" : "Posted");
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
                "\n\nPlayers: ** " + getPlayerCount() + "**" +
                    "\nVotes: **" + currentKickVotes + "/" + numberOfVotesNeeded + "**" +
                        (kickvoteThresholdMet ? "**\n\nThreshold " + (currentKickVotes > numberOfVotesNeeded ? "Crossed" : "Met") + "!**" +
                                                                   "\n**Don't Forget to React with ✅ when you see the leave message!**" +
                                                                    "\n**If you haven't voted yet, please do so! If you have, thanks!**" : "")
        ));

        if (numOfBumps >= 5) {
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

        String result = kickEmoji.getAsMention() + "**" + targetKickvotePlayer + "**" +
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
}
