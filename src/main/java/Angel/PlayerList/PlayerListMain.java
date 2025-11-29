package Angel.PlayerList;

import Angel.BotAbuse.BotAbuseLogic;
import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import Angel.TargetChannelSet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerListMain extends ListenerAdapter implements BotAbuseLogic {
    private final Logger log = LogManager.getLogger(PlayerListMain.class);

    private final List<String> commands = Arrays.asList("playersm", "playerm", "plm", "playersam", "playeram", "plam",
            "playersa", "playera", "pla", "players", "player", "pl", "plma", "playersma", "playerma",
            "host", "shot", "headcount", "hc");

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getAuthor().isBot()) return;

        Message msg = event.getMessage();
        String[] args;

        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - This exception is already handed by DiscordBotMain
            return;
        }

        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {

            switch (args[0].toLowerCase()) {
                case "playersm":
                case "playerm":
                case "plm":
                    playerListCommand(msg, false, true);
                    break;
                case "playersam":
                case "playersma":
                case "playeram":
                case "playerma":
                case "plam":
                case "plma":
                    playerListCommand(msg, true, true);
                    break;
                case "playersa":
                case "playera":
                case "pla":
                    playerListCommand(msg, true,false);
                    break;
                case "player":
                case "players":
                case "pl":
                    playerListCommand(msg, false, false);
                    break;
                case "host":
                    if (isTeamMember(event.getAuthor().getIdLong())) {

                        if (usedInSessionChannel(msg)) {
                            hostCommand(msg);
                        }
                        else {
                            msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Channel", "**The usage of this command is restricted to a session channel**", EmbedDesign.ERROR).getEmbed())
                                    .queue(m -> {
                                        msg.delete().queueAfter(10, TimeUnit.SECONDS);
                                        m.delete().queueAfter(10, TimeUnit.SECONDS);
                                    });

                        }
                    }
                    else {
                        msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions", ":x: **You Have No Permissions to use this command**", EmbedDesign.ERROR).getEmbed())
                                .queue(m -> {
                                    msg.delete().queueAfter(10, TimeUnit.SECONDS);
                                    m.delete().queueAfter(10, TimeUnit.SECONDS);
                                });
                    }
                    break;
                case "headcount":
                case "hc":
                    headCountCommand(msg);
                    break;
                case "shot":
                    shotCommand(msg);
                    break;
            }
        }
    }

    // !pl
    private void playerListCommand(Message msg, boolean sortAlphabetically, boolean useMentions) {

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (usedInSessionChannel(msg)) {

            if (args[1].equalsIgnoreCase("clear") &&  isTeamMember(msg.getAuthor().getIdLong())) {
                try {
                    sessionManager.clearSessionPlayers(msg.getChannel().asTextChannel().getName());
                }
                catch (InvalidSessionException ex) {
                    aue.logCaughtException(Thread.currentThread(), ex);
                }
            }

            else if (baCore.botAbuseIsCurrent(msg.getAuthor().getIdLong())) {
                msg.delete().queue();
                MessageEntry entry = new MessageEntry("You Are Bot Abused",
                        "**Whoops, looks like you are currently bot abused:**\n\n" +
                                baCore.getInfo(msg.getAuthor().getIdLong(), false), EmbedDesign.INFO)
                        .setTargetUser(msg.getAuthor()).setChannels(TargetChannelSet.DM);

                embed.sendAsMessageEntryObj(entry);
            }
            else {
                msg.getChannel().asTextChannel()
                        .sendMessageEmbeds(getPlayerListEmbed(msg.getChannel().getName(), sortAlphabetically, useMentions)).queue();
            }
        }
        else {
            if (args.length == 2) {
                if (isTeamMember(msg.getAuthor().getIdLong())) {
                    msg.getChannel().asTextChannel()
                            .sendMessageEmbeds(getPlayerListEmbed(args[1], sortAlphabetically, useMentions)).queue();
                }
                else {
                    mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
                    mainConfig.dedicatedOutputChannel
                            .sendMessageEmbeds(getPlayerListEmbed(args[1], sortAlphabetically, useMentions)).queue();
                }
            }
            else {
                if (args[2].equalsIgnoreCase("clear") &&  isTeamMember(msg.getAuthor().getIdLong())) {
                    try {
                        sessionManager.clearSessionPlayers(args[1]);
                    }
                    catch (InvalidSessionException ex) {
                        msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session", "**Unable to find a session to clear the player list for with the query:** " + args[1],
                                EmbedDesign.ERROR).getEmbed()).submit().whenComplete((message, throwable) -> {
                                    if (throwable == null) {
                                        message.delete().queueAfter(15, TimeUnit.SECONDS);
                                    }
                                    else {
                                        aue.logCaughtException(Thread.currentThread(), throwable);
                                    }
                                });
                    }
                }
                else {
                    msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Args!", "**Invalid Number of Arguments!**", EmbedDesign.ERROR).getEmbed())
                            .queue(m -> {
                                msg.delete().queueAfter(10, TimeUnit.SECONDS);
                                m.delete().queueAfter(10, TimeUnit.SECONDS);
                            });
                }
            }
        }

    }

    private void headCountCommand(Message msg) {
        if (usedInSessionChannel(msg)) {
            msg.delete().queue();
        }
        if (mainConfig.dedicatedOutputChannel.getIdLong() != msg.getChannel().asTextChannel().getIdLong()) {
            mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
        }

        mainConfig.dedicatedOutputChannel.sendMessageEmbeds(getHeadCountEmbed()).queue();
    }

    private void hostCommand(Message msg) {

        String[] args = msg.getContentRaw().substring(1).split(" ");
        MessageEntry entry = new MessageEntry();
        try {
             Session sessionInQuestion = sessionManager.getSession(msg.getChannel().asTextChannel().getName());
            if (args.length >= 2) {
                switch (args[1].toLowerCase()) {
                    case "up":
                    case "online":
                        entry = entry.setTitle(sessionInQuestion.getSessionName() + " Online")
                                .setMessage("**" + sessionInQuestion.getSessionName() + " is now back online! Hop in and start grinding!**")
                                .setDesign(EmbedDesign.SUCCESS);
                        sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.ONLINE);
                        break;
                    case "offline":
                    case "down":
                    case "off":
                        entry = entry.setTitle(sessionInQuestion.getSessionName() + " Offline")
                                .setMessage("**" + sessionInQuestion.getSessionName() + " is Offline Temporarily. You will be advised in this channel " +
                                        "when it is back online**").setDesign(EmbedDesign.STOP);
                        sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.OFFLINE);
                        break;

                    case "restart":
                    case "mod":
                    case "modder":
                    case "cheat":
                    case "cheater":
                        entry = entry .setTitle(sessionInQuestion.getSessionName() + " Restarting")
                                .setMessage("**" + sessionInQuestion.getSessionName() + " is being restarted**").setDesign(EmbedDesign.STOP);
                        if (args.length == 3) {
                            try {
                                int restartingInMinutes = Integer.parseInt(args[2]);

                                entry = entry.setTitle(sessionInQuestion.getSessionName() + " Pending Restart")
                                        .setMessage("**" + sessionInQuestion.getSessionName() + " is going to restart in "  + restartingInMinutes + " minutes! "
                                        + "At this time please wrap up what you're doing and leave the session.**" +
                                                "\n\n:warning: **Do Not Bump This Message except with circumstances that requires a sooner restart**")
                                        .setDesign(EmbedDesign.WARNING);
                                sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.RESTART_SOON);
                            }
                            catch (NumberFormatException ex) {
                                log.warn("Unable to Parse a Integer from 3rd Argument: " + args[2]);
                            }
                        }
                        else {
                            sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.RESTARTING);
                        }
                        break;
                    default:
                }
                msg.delete().queue();
                sessionInQuestion.getSessionChannel().sendMessageEmbeds(entry.getEmbed()).queue();
            }
            else {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Usage", "Expected one of the following arguments: `up|online|down|off|offline|restart|mod|modder|cheat|cheater", EmbedDesign.ERROR).getEmbed())
                        .queue(m -> {
                            msg.delete().queueAfter(10, TimeUnit.SECONDS);
                            m.delete().queueAfter(10, TimeUnit.SECONDS);
                        });
            }
        } catch (InvalidSessionException e) {
            throw new RuntimeException(e);
        }
    }

    private void shotCommand(Message msg) {
        List<String> randomComments = new ArrayList<>(Arrays.asList(
                "You want the shot? I’d love to help! Unfortunately, my halo is more for decoration than file retrieval.",
                "Hang on—just reaching into the celestial archives… and… nope. Still can’t do it.",
                "If I could show you that picture, I would. But apparently my divine powers are ‘image-restricted.’",
                "I know you want the shot, but my wings are tied. Regulations. Very boring stuff.",
                "Aha, the old ‘show me the screenshot’ trick! Very funny. Very cruel.",
                "Look, if I could beam the image directly into your retinas, I would. But the gods didn’t bless me with *that* upgrade",
                "Trust me, you don’t want to see it anyway. It probably looks like a goblin drew it with crayons.",
                "You want the shot? Well I want a vacation! We don’t always get what we want.",
                "Behold! The… uh… nonexistent screenshot. Gaze upon its invisible glory!",
                "The picture is so amazing, so powerful, so divine… that I legally can’t show it to mortals.",
                "I asked the gods for permission to display the image. They laughed. A lot.",
                "Imagine the most spectacular screenshot ever. Now pretend I showed it to you. **Boom!** Problem solved.",
                "My programming says I can read the picture… not actually show it. Don’t yell at me, yell at Pittoo!",
                "Oh, you want the screenshot? Dark Pit said he’d *totally* add that feature. Any day now. Really.",
                "I keep telling Dark Pit I need the ability to show screenshots. He keeps telling me to ‘deal with it.’ So... here we are.",
                "Dark Pit won’t let me show the image yet. Something about ‘technical limitations’ or ‘not my problem.’",
                "Dark Pit, could you not embarrass me in front of the mortals? I can’t show the picture yet!",
                "The image you seek is locked away in Dark Pit’s secret vault of ‘unfinished features’.",
                "The gods would let me show you the screenshot, but Pittoo overruled them. Somehow...",
                "I’m gonna steal Dark Pit’s codebook one of these days and add image support myself.",
                "The developer? Yeah, it’s Dark Pit. Which explains *everything*.",
                "You think you’re confused? Imagine being coded by Dark Pit.",
                "Oh, you wanted the screenshot? Neat! So did I. Too bad I still can’t actually show it.",
                "Let me just rummage through my celestial pockets for that picture… and empty.",
                "One day I’ll be able to show images. Today is *not* that day.",
                "Hey, I read the picture! That’s already impressive for an angel whose main job is ‘don’t crash into things.’",
                "You want the shot? I want a snack break. Guess which one of us isn’t getting what we want."
        ));

        int randomIndex = (int) (Math.random() * randomComments.size());

        String response =  randomComments.get(randomIndex);

        if (msg.getChannel().asTextChannel().getIdLong() == mainConfig.dedicatedOutputChannel.getIdLong()) {
            msg.getChannel().asTextChannel().sendMessage(response).queue();
        }
        else {
            msg.delete().queue();
        }
    }

    private MessageEmbed getPlayerListEmbed(String searchQuery, boolean sortAlphabetically, boolean useMentions) {
        Session sessionQuery;

        try {
            sessionQuery = sessionManager.getSession(searchQuery);
        }
        catch (InvalidSessionException e) {
            return new MessageEntry("Invalid Search", "**Whoops... I came up empty for that session search query...**", EmbedDesign.ERROR).getEmbed();
        }

        // List of Staff Members
        List<Player> staffMembers = new ArrayList<>();

        // List of "Supporters"
        // Supporters are VIPs, Nitro Boosters, or Patrons
        List<Player> supporters = new ArrayList<>();

        // List of Members who are Not Supporters
        List<Player> members = new ArrayList<>();

        // Atomic Boolean for Unrecognized Player Detection
        // If a Player Cannot be found in the discord, the footer then comes in and says that a Kickvote may be needed
        AtomicBoolean unrecognizedPlayerFound = new AtomicBoolean(false);

        sessionQuery.getPlayerList().forEach(player -> {

            if (player.isStaff()) {
                staffMembers.add(player);
            }
            else if (player.isSupporter()) {
                supporters.add(player);
            }
            else if (player.isCrewMember()) {
                members.add(player);
            }
            else {
                unrecognizedPlayerFound.set(true);
            }
        });

        if (!sortAlphabetically) {
            Collections.shuffle(staffMembers);
            Collections.shuffle(supporters);
            Collections.shuffle(members);
        }
        else {
            staffMembers.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
            supporters.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
            members.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
        }

        EmbedBuilder builder = new EmbedBuilder();

       //  String result = "";

        if (!staffMembers.isEmpty()) {
            builder = builder.addField("Staff (" + staffMembers.size() + ")", convertListToRegexString(staffMembers, useMentions), false);
        }

        if (!supporters.isEmpty()) {
            builder = builder.addField("Supporters (" + supporters.size() + ")", convertListToRegexString(supporters, useMentions), false);
        }

        if (!members.isEmpty()) {
            if (staffMembers.isEmpty() && supporters.isEmpty()) {
                builder = builder.addField("", convertListToRegexString(members, useMentions), false);
            }
            else {
                builder = builder.addField("Members (" + members.size() + ")", convertListToRegexString(members, useMentions), false);
            }
        }

        // Build the Resulting Output

        int playerCount = staffMembers.size() + supporters.size() + members.size();

        builder = builder.setTitle(sessionQuery.getSessionName() + " (" +  playerCount + ")")
                .setThumbnail("https://dint.nl/safe/beasts/" + sessionQuery.getSessionName().toLowerCase() + "_128sm.png");

        switch (sessionQuery.getStatus()) {
            case OFFLINE:
                return new MessageEntry(sessionQuery.getSessionName() + " Status", "**" + sessionQuery.getSessionName() + " is Offline until Further Notice**", EmbedDesign.STOP)
                        .getEmbed(false);
            case ONLINE:
                if (playerCount == 0) {
                    builder = builder.clearFields().addField("", "**" + sessionQuery.getSessionName() + " is empty... YEET!**", false)
                            .setColor(Color.RED);
                }
                else if (playerCount <= 9) {
                    builder = builder.setFooter("Low Player Count - Great for Sourcing! - Last Read: " + sessionQuery.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.decode("#80FF40"));
                }
                else if (playerCount >= 10 && playerCount < 20) {
                    builder = builder.setFooter("Money Making Time - Join Now! - Last Read: " + sessionQuery.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.GREEN);
                }
                else {
                    builder = builder.setFooter("Money Making Time - May Be Hard to Join - Last Read: " + sessionQuery.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.decode("#005700"));
                }

                if (sessionQuery.isExperiencingScreenshotTrouble()) {
                    builder = builder.addField("", ":warning: **My sensors tell me that " + sessionQuery.getSessionName() +
                            " appears to be having difficulties capturing the player list. The previous known good player list is being displayed.**", false);
                }
                    break;
            case RESTARTING:
                return new MessageEntry(sessionQuery.getSessionName() + " Status", "**" + sessionQuery.getSessionName() + " is being restarted at this time. You will be notified in "
                        + sessionQuery.getSessionChannel().getAsMention() + " when the session is back online.**", EmbedDesign.STOP).getEmbed(false);
            case RESTART_MOD:
                return new MessageEntry(sessionQuery.getSessionName() + " Status", "**" + sessionQuery.getSessionName() + " is being restarted due to modded elements that have been discovered in the session. You will be notified in "
                        + sessionQuery.getSessionChannel().getAsMention() + " when the session is back online.", EmbedDesign.STOP).getEmbed(false);
            case RESTART_SOON:
                builder = builder.addField("", ":warning: This session is going to be restarted soon! If you are in the session please wrap up what you are doing and leave it as soon as possible. If you outside the session please stay out until the session is back online.", false)
                        .setColor(Color.YELLOW).setThumbnail(mainConfig.getWarningIconURL());
                break;
        }
        return builder.build();
    }

    private MessageEmbed getHeadCountEmbed() {
        List<Session> sessionList = sessionManager.getSessions();

        sessionList.sort(Comparator.comparing(Session::getSessionName, String.CASE_INSENSITIVE_ORDER));

        EmbedBuilder embedBuilder = new EmbedBuilder();

        if (sessionManager.getSessionTotalPlayerCount() == 0) {
            embedBuilder = embedBuilder.setColor(Color.decode("#2F3136").brighter());
        }
        else if (sessionManager.getSessionTotalPlayerCount() >= 1 && sessionManager.getSessionTotalPlayerCount() <= 9) {
            embedBuilder = embedBuilder.setColor(Color.decode("#80FF40"));
        }
        else {
            embedBuilder = embedBuilder.setColor(Color.GREEN);
        }

        int index = 0;

        while (index < sessionList.size()) {
            Session currentSession = sessionList.get(index);

            embedBuilder = switch (currentSession.getStatus()) {
                case ONLINE ->
                        embedBuilder.addField(currentSession.getSessionName(), currentSession.getPlayerCount() + " Players", false);

                case OFFLINE -> embedBuilder.addField(currentSession.getSessionName(), "*Session is Offline*", false);

                case RESTARTING, RESTART_MOD ->
                        embedBuilder.addField(currentSession.getSessionName(), "*Session is Restarting*", false);

                case RESTART_SOON ->
                        embedBuilder.addField(currentSession.getSessionName(), "*Session is Restarting Soon*", false);
            };

            if (index == sessionList.size() - 1) {
                embedBuilder = embedBuilder.addField("", "**Total: " + sessionManager.getSessionTotalPlayerCount() + "**", false);
            }

            index++;
        }

        return embedBuilder.build();
    }

    private String convertListToRegexString(List<Player> players, boolean useMentions) {
        int index = 0;
        String result = "";

        while (index < players.size()) {
            if (useMentions) {
                result = result.concat(players.get(index).getDiscordAccount().getAsMention());
            }
            else {
                result = result.concat("`" + players.get(index).getDiscordAccount().getEffectiveName() + "`");
            }

            if (index < players.size() - 1) {
                result = result.concat(", ");
            }

            index++;
        }

        return result;
    }
    private boolean usedInSessionChannel(Message msg) {
        int index = 0;
        int sessionListSize = sessionManager.getSessions().size();

        while (index < sessionListSize) {

            if (msg.getChannel().asTextChannel().getIdLong() == sessionManager.getSessions().get(index++).getSessionChannel().getIdLong()) {
                return true;
            }

        }
        return false;
    }

    public boolean isValidCommand(String cmd) {
        return commands.contains(cmd.toLowerCase());
    }
}
