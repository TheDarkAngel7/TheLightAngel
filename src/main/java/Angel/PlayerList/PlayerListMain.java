package Angel.PlayerList;

import Angel.BotAbuse.BotAbuseLogic;
import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import Angel.TargetChannelSet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

                        if (sessionManager.usedInSessionChannel(msg)) {
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

        List<Session> accessibleSessions = sessionManager.getAccessibleSessions(msg.getAuthor().getIdLong());

        if (accessibleSessions.isEmpty()) {
            msg.getChannel().sendMessageEmbeds(new MessageEntry("Unable to Find Active Session",
                    "**Unable to Find an active session, this could be coming back from a fresh restart... or all sessions could be down right now...**", EmbedDesign.ERROR)
                    .getEmbed(false)).queue(m -> {
                msg.delete().queueAfter(10, TimeUnit.SECONDS);
                m.delete().queueAfter(10, TimeUnit.SECONDS);
            });
            return;
        }
        if (sessionManager.usedInSessionChannel(msg)) {
            try {
                Session targetSession = sessionManager.getSessionByChannel(msg.getChannel().getIdLong());
                if (args.length >= 2) {
                    try {
                        Session otherSession = sessionManager.getSessionByName(args[1]);

                        /*
                        If a team member searches for another session in the session channel,
                        or if a player searches for the session in the same session channel as the one they're searching for
                         */
                        if (isTeamMember(msg.getAuthor().getIdLong()) || msg.getChannel().getIdLong() == otherSession.getSessionChannel().getIdLong()) {
                            otherSession.getPlayerListMessage(msg, sortAlphabetically, useMentions)
                                    .setTargetChannel(msg.getChannel().asTextChannel()).getPlayerListEmbedAction().queue();
                        }
                        else {
                            msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions",
                                    "**I cannot print that out here but I will print it out in " + mainConfig.dedicatedOutputChannel.getAsMention() + "**", EmbedDesign.ERROR).getEmbed())
                                    .queue(m -> {
                                        msg.delete().queueAfter(30, TimeUnit.SECONDS);
                                        m.delete().queueAfter(30, TimeUnit.SECONDS);
                                    });
                            otherSession.getPlayerListMessage(msg, sortAlphabetically, useMentions)
                                    .setTargetChannel(mainConfig.dedicatedOutputChannel).getPlayerListEmbedAction().queue();
                        }
                    }
                    catch (InvalidSessionException ex) {
                        if (isTeamMember(msg.getAuthor().getIdLong())) {
                            playerListConfig(msg, true);
                        }
                        else {
                            msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session",
                                            "**It seems you didn't provide me a valid session name, but if I were you I would go to " + mainConfig.dedicatedOutputChannel.getAsMention() + " for that**", EmbedDesign.ERROR).getEmbed())
                                    .queue(m -> {
                                        msg.delete().queueAfter(30, TimeUnit.SECONDS);
                                        m.delete().queueAfter(30, TimeUnit.SECONDS);
                                    });
                        }
                    }
                }

                else if (baCore.botAbuseIsCurrent(msg.getAuthor().getIdLong())) {
                    mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention() + ", because you are bot abused, the output got redirected here:").queue();
                    targetSession.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(mainConfig.dedicatedOutputChannel).getPlayerListEmbedAction().queue();

                    msg.delete().queue();
                    MessageEntry entry = new MessageEntry("You Are Bot Abused",
                            "**Whoops, looks like you are currently bot abused:**\n\n" +
                                    baCore.getInfo(msg.getAuthor().getIdLong(), false), EmbedDesign.INFO)
                            .setTargetUser(msg.getAuthor()).setChannels(TargetChannelSet.DM);

                    embed.sendAsMessageEntryObj(entry);
                }
                else {
                    PlayerListMessage playerListMessage;

                    // If the player is not a team member (team members bypass cooldown) and the cooldown is active, then the output gets redirected
                    if (!isTeamMember(msg.getAuthor().getIdLong()) && targetSession.isCooldownActive()) {
                         playerListMessage = targetSession.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(mainConfig.dedicatedOutputChannel);

                        mainConfig.dedicatedOutputChannel.sendMessage("**" + msg.getAuthor().getAsMention() + " the output of `" + mainConfig.commandPrefix + "pl` was redirected here as "
                                + targetSession.getSessionChannel().getAsMention() + " already had this command used less than " + targetSession.getCooldownDuration() + " minutes ago!**" +
                                "\n\nTime Remaining: **" + targetSession.getTimerUntilCooldownIsOver() + "**").queue();
                        msg.getChannel().sendMessage("**Cooldown Enabled for the session channel!** See " + mainConfig.dedicatedOutputChannel.getAsMention())
                                .queue(m -> {
                                    msg.delete().queueAfter(10, TimeUnit.SECONDS);
                                    m.delete().queueAfter(10, TimeUnit.SECONDS);
                                });
                        log.info("{}'s {}pl Output Stopped and Redirected as Cooldown is Enabled - {} Left!", msg.getMember().getEffectiveName(), mainConfig.commandPrefix, targetSession.getTimerUntilCooldownIsOver());
                    }
                    else {
                        playerListMessage = targetSession.getPlayerListMessage(msg, sortAlphabetically, useMentions);
                    }

                    playerListMessage.getPlayerListEmbedAction().queue();
                }
            }
            catch (InvalidSessionException e) {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Channel", "**Whoops... this does not appear to be a valid session channel!**", EmbedDesign.ERROR).getEmbed())
                        .queue(m -> {
                            msg.delete().queueAfter(10, TimeUnit.SECONDS);
                            m.delete().queueAfter(10, TimeUnit.SECONDS);
                        });
            }
        }

        else if (msg.getChannel().getIdLong() == mainConfig.dedicatedOutputChannel.getIdLong() || msg.getChannelType().equals(ChannelType.PRIVATE)) {
           try {
               if (args.length == 1) {

                   if (accessibleSessions.size() == 1) {
                       Session session = accessibleSessions.getFirst();

                       session.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(msg.getChannel())
                               .getPlayerListEmbedAction().queue();
                   }
                   else {
                       msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session", "**Whoops... there appears to be more than one session running, I was expecting an argument for a session!**" +
                                       "\n\n**You may use `" + mainConfig.commandPrefix + "headcount` to see what sessions are available.**", EmbedDesign.ERROR).getEmbed())
                               .queue();
                   }
               }
               else if (args.length == 2) {
                   Session session = sessionManager.getSessionByName(args[1]);
                   PlayerListMessage playerListMessage = session.getPlayerListMessage(msg, sortAlphabetically, useMentions);

                   if (session.isSessionChannelAccessible(msg.getAuthor().getIdLong())) {
                       playerListMessage.sortListAlphabetically(sortAlphabetically).useMentions(false)
                               .setTargetChannel(msg.getChannel()).getPlayerListEmbedAction().queue();
                   }
                   else {
                       msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions", "**You do not have permissions to view these players!**", EmbedDesign.ERROR).getEmbed()).queue();
                   }

               }
               else {
                   msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Arguments", "**Whoops... I was expecting just 1 argument... but found " + (args.length - 1) + "**", EmbedDesign.ERROR).getEmbed())
                           .queue(m -> {
                               m.delete().queueAfter(10, TimeUnit.SECONDS);
                               msg.delete().queueAfter(10, TimeUnit.SECONDS);
                           });
               }
           }
           catch (InvalidSessionException e) {
               msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session", "**Whoops... this does not appear to belong to a session that's currently running!**" +
                               "\n\n**You may use `" + mainConfig.commandPrefix + "headcount` to see what sessions are available.**", EmbedDesign.ERROR).getEmbed())
                       .queue();
           }

        }

        else if (msg.getChannel().getIdLong() == mainConfig.managementChannel.getIdLong() || msg.getChannel().getIdLong() == mainConfig.discussionChannel.getIdLong()) {
            if (args.length == 1) {
                // Reserved for !pl Help in these channels
            }
            else if (args.length >= 2) {
                try {
                    Session targetSession = sessionManager.getSessionByName(args[1]);
                    targetSession.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(msg.getChannel().asTextChannel())
                            .getPlayerListEmbedAction().queue();
                }
                catch (InvalidSessionException e) {
                    playerListConfig(msg, false);
                }
            }
        }

        else {
            if (args.length == 2) {
                try {
                    Session session = sessionManager.getSessionByName(args[1]);
                    if (isTeamMember(msg.getAuthor().getIdLong())) {
                        session.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(msg.getChannel().asTextChannel()).getPlayerListEmbedAction().queue();
                    }
                    else {
                        mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention()).queue();

                        if (session.isSessionChannelAccessible(msg.getMember())) {
                            session.getPlayerListMessage(msg, sortAlphabetically, useMentions).setTargetChannel(mainConfig.dedicatedOutputChannel).getPlayerListEmbedAction().queue();
                        }
                        else {
                            mainConfig.dedicatedOutputChannel.sendMessageEmbeds(new MessageEntry("No Permissions", "**You do not have permissions to view these players!**", EmbedDesign.ERROR).getEmbed()).queue();
                        }
                    }
                }
                catch (InvalidSessionException e) {
                    mainConfig.dedicatedOutputChannel.sendMessageEmbeds(
                            new MessageEntry("Invalid Search", "**Whoops... this does not appear to be a valid search!**",  EmbedDesign.ERROR).getEmbed())
                            .queue();
                }

            }
            else {
                if (args.length == 3 && args[2].equalsIgnoreCase("clear") &&  isTeamMember(msg.getAuthor().getIdLong())) {
                    try {
                        Session session = sessionManager.getSessionByName(args[1]);

                        session.clearPlayerList();

                        msg.getChannel().sendMessage("**" + msg.getMember().getEffectiveName() + " has cleared the player list of " + session.getSessionName() + "**").queue();
                        msg.delete().queue();
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

    private void playerListConfig(Message msg, boolean usedInSessionChannel) {
        String[] args = msg.getContentRaw().substring(1).split(" ");


        switch (args[1].toLowerCase()) {
            case "clear":
                if (usedInSessionChannel) {
                    try {
                        Session session = sessionManager.getSessionByChannel(msg.getChannel().asTextChannel());
                        session.clearPlayerList();
                        msg.getChannel().sendMessage("**" + msg.getMember().getEffectiveName() + " has cleared the player list for " + session.getSessionName() + "!**").queue();

                    } catch (InvalidSessionException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    msg.getChannel().sendMessage("**" + msg.getMember().getEffectiveName() + " has cleared the player list for all sessions!**").queue();
                    sessionManager.clearAllSessionPlayers();
                }
                break;
            case "cooldown":
            case "cd":
                EmbedBuilder embed = new EmbedBuilder().setThumbnail("attachment://safe-logo.png")
                        .setTitle("!pl Cooldown Status").setColor(Color.decode("#2F3136"));
                List<Session> sessions = sessionManager.getSessions();

                int index = 0;

                do {
                    Session currentSession = sessions.get(index++);
                    embed = embed.addField(currentSession.getSessionName(), "Status: " + (currentSession.isCooldownEnabled() ?
                            "**Enabled**" +
                                    "\nCooldown Duration: **" + currentSession.getCooldownDuration() + " Minutes**" +
                                    (currentSession.getMinNumberOfPlayersInSessionForCooldown() > 0 ?
                                            "\nMin Number of Players: **" + currentSession.getMinNumberOfPlayersInSessionForCooldown() + " Players**\n": "") : "**Disabled**"), false);
                } while (index < sessions.size());

                msg.getChannel().sendMessageEmbeds(embed.build()).setFiles(getSAFECrewLogo()).queue();
                break;
            case "enablecooldown":
            case "enablecd":
            case "ecd":
            case "updatecooldown":
            case "updatecd":
            case "ucd":
                int cooldownDuration;
                int minNumberOfPlayers = 0;
                try {
                    cooldownDuration = Integer.parseInt(args[2]);
                }
                catch (NumberFormatException ex) {
                    msg.delete().queue();
                    msg.getChannel().sendMessage("Whoops, that was an error! Syntax: `" + mainConfig.commandPrefix + "pl enablecooldown <minutes>`").queue();
                    return;
                }
                Session session = null;

                if (usedInSessionChannel) {
                    try {
                        session = sessionManager.getSessionByChannel(msg.getChannel().getIdLong());
                    }
                    catch (InvalidSessionException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                try {
                    // Try to Parse minNumberOfPlayers from 4th Argument
                    // !pl enablecooldown <cooldownDuration> [minNumberOfPlayers]
                    // NumberFormatException would get thrown if the argument does not exist

                    minNumberOfPlayers = Integer.parseInt(args[3]);

                    if (usedInSessionChannel) {
                        session.enablePlayerListCooldown(cooldownDuration, minNumberOfPlayers);
                    }
                    else {
                        int finalMinNumberOfPlayers = minNumberOfPlayers;
                        sessionManager.getSessions().forEach(s -> s.enablePlayerListCooldown(cooldownDuration, finalMinNumberOfPlayers));
                    }
                }
                catch (NumberFormatException ex) {
                    log.warn("Unable to find a Minimum Number Of Players: {}", msg.getContentRaw());

                    if (usedInSessionChannel) {
                        session.enablePlayerListCooldown(cooldownDuration);
                    }
                    else {
                        sessionManager.getSessions().forEach(s -> s.enablePlayerListCooldown(cooldownDuration));
                    }
                }

                if (!usedInSessionChannel) {
                    msg.getChannel().sendMessage("**" + msg.getMember().getEffectiveName() + " has enabled the player list cooldown for all sessions!**" +
                            "\n\nCooldown Duration: **" + cooldownDuration + " Minutes**" +
                            (minNumberOfPlayers > 0 ? "\nMin Number of Players: **" + minNumberOfPlayers + " Players**" : "") +
                            "\n\n**You may use `" + mainConfig.commandPrefix + "pl cooldown` to view the cooldown settings**").queue();
                }
                break;
            case "disablecooldown":
            case "disablecd":
            case "dcd":
                if (usedInSessionChannel) {
                    try {
                        Session sessionInQuestion = sessionManager.getSessionByChannel(msg.getChannel().getIdLong());

                        sessionInQuestion.disablePlayerListCooldown();
                    }
                    catch (InvalidSessionException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    msg.getChannel().sendMessage("**" + msg.getMember().getEffectiveName() + " has disabled the player list cooldown for all sessions**").queue();
                    sessionManager.getSessions().forEach(Session::disablePlayerListCooldown);
                }
                break;
        }
    }

    private void headCountCommand(Message msg) {
        if (sessionManager.getSessions().isEmpty()) {
            msg.getChannel().sendMessageEmbeds(new MessageEntry("Unable to Find Active Session",
                    "**Unable to Find an active session for counting heads...**", EmbedDesign.ERROR)
                    .getEmbed(false)).queue(m -> {
                msg.delete().queueAfter(10, TimeUnit.SECONDS);
                m.delete().queueAfter(10, TimeUnit.SECONDS);
            });
            return;
        }
        if (isTeamMember(msg.getAuthor().getIdLong())) {
            getHeadCountEmbed(msg.getMember(), msg.getChannel()).queue();
        }
        else if (msg.getChannelType().equals(ChannelType.PRIVATE)) {
            Member m = getGuild().getMemberById(msg.getAuthor().getIdLong());
            if (m == null) {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("No Access", "**You Do Not have access to this command as you are not in the SAFE Crew Discord Server!",
                        EmbedDesign.ERROR).getEmbed()).queue();
            }
            else {
                getHeadCountEmbed(m, msg.getChannel()).queue();
            }
        }
        else {
            if (sessionManager.usedInSessionChannel(msg)) {
                msg.delete().queue();
            }
            if (mainConfig.dedicatedOutputChannel.getIdLong() != msg.getChannel().asTextChannel().getIdLong()) {
                mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention()).queue();
            }

            getHeadCountEmbed(msg.getMember(), mainConfig.dedicatedOutputChannel).queue();
        }
    }

    private void hostCommand(Message msg) {

        String[] args = msg.getContentRaw().toLowerCase().substring(1).split(" ");

        try {
             Session sessionInQuestion = sessionManager.getSessionByName(msg.getChannel().asTextChannel().getName());
            if (args.length >= 2) {
                switch (args[1].toLowerCase()) {
                    case "up":
                    case "online":
                        // We Do Not Send our own MessageEntry objects in either online or offline states
                        // as these are handled by the setSessionState method
                        sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.FRESH_ONLINE);
                        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

                        service.schedule(() -> {
                            try {
                                sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.ONLINE);
                            }
                            catch (InvalidSessionException e) {
                                throw new RuntimeException(e);
                            }
                        }, 5, TimeUnit.MINUTES);
                        break;
                    case "offline":
                    case "down":
                    case "off":
                        sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.OFFLINE);
                        break;

                    case "restart":
                    case "mod":
                    case "modder":
                    case "cheat":
                    case "cheater":
                        MessageEntry entry = new MessageEntry();
                        entry = entry .setTitle(sessionInQuestion.getSessionName() + " Restarting")
                                .setMessage("**" + sessionInQuestion.getSessionName() + " is being restarted**").setDesign(EmbedDesign.STOP);
                        if (args.length == 3) {
                            try {
                                int restartingInMinutes = Integer.parseInt(args[2]);

                                entry = entry.setTitle(sessionInQuestion.getSessionName() + " Pending Restart")
                                        .setMessage("**" + sessionInQuestion.getSessionName() + " is going to restart in " + restartingInMinutes + " minutes! "
                                        + "At this time please wrap up what you're doing and leave the session.**" +
                                                "\n\n:warning: **Do Not Bump This Message except with circumstances that requires a sooner restart**")
                                        .setDesign(EmbedDesign.WARNING);
                            }
                            catch (NumberFormatException ex) {
                                log.warn("Unable to Parse a Integer from 3rd Argument: " + args[2]);
                                entry = entry.setTitle(sessionInQuestion.getSessionName() + " Pending Restart")
                                        .setMessage("**" + sessionInQuestion.getSessionName() + " is going to restart in 30 minutes! "
                                                + "At this time please wrap up what you're doing and leave the session.**" +
                                                "\n\n:warning: **Do Not Bump This Message except with circumstances that requires a sooner restart**")
                                        .setDesign(EmbedDesign.WARNING);
                            }
                            sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.RESTART_SOON);
                            sessionInQuestion.getSessionChannel().sendMessageEmbeds(entry.getEmbed()).queue();
                        }
                        else {
                            sessionManager.setSessionState(sessionInQuestion.getSessionName(), SessionStatus.RESTARTING);
                        }

                        break;
                    default:
                }
                msg.delete().queue();
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
                "You want the shot? I’d love to help! Unfortunately, my halo is more for decoration than file retrieval for this channel.",
                "Hang on—just reaching into the celestial archives… and… nope. Still can’t do it.",
                "If I could show you that picture, I would. But apparently my divine powers are ‘image-restricted’ in this channel",
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
                "The image you seek is locked away in Dark Pit’s secret vault of ‘staff only commands’.",
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

        String response = randomComments.get(randomIndex);

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (msg.getChannel().getIdLong() == mainConfig.dedicatedOutputChannel.getIdLong() || msg.getChannelType().equals(ChannelType.PRIVATE)) {
            Member m = getGuild().getMemberById(msg.getAuthor().getIdLong());

            if (m == null) {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("No Access", "**You Do Not have access to this command as you are not in the SAFE Crew Discord Server!",
                        EmbedDesign.ERROR).getEmbed()).queue();
            }
            else {
                if (args.length == 1) {
                    List<Session> accessibleSessions = sessionManager.getAccessibleSessions(msg.getAuthor().getIdLong());
                    if (accessibleSessions.size() == 1) {
                        try {
                            Session session = accessibleSessions.getFirst();

                            if (session.isSessionChannelAccessible(msg.getAuthor().getIdLong())) {
                                session.getPlayerListMessage(msg).setTargetChannel(msg.getChannel())
                                        .getScreenshotEmbedAction().queue();
                            }
                            else {
                                msg.getChannel().sendMessage(response).queue();
                                msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions", "**You do not have permissions to view the screenshot!**", EmbedDesign.ERROR).getEmbed()).queue();
                            }
                        }
                        catch (IOException e) {
                            log.error("Unable to send screenshot to {}. Reason: {}",
                                    "#" + mainConfig.dedicatedOutputChannel.getName(), e.getMessage());
                            msg.getChannel().sendMessage(response).queue();
                        }
                    }
                    else {
                        mainConfig.dedicatedOutputChannel.sendMessageEmbeds(
                                new MessageEntry("Invalid Session", "**Unable to Find a Session from no search as there is " + (sessionManager.getSessions().size() > 1 ? "more than 1 session running!**"
                                        + "\n\n**You may use `" + mainConfig.commandPrefix + "headcount` to see what sessions are available.**" :
                                        " **no sessions running. This may because I just restarted and I'm waiting for the first player list from the host.**") + "**",
                                        EmbedDesign.ERROR).getEmbed()).queue();
                    }
                }
                else {
                    try {
                        Session session = sessionManager.getSessionByName(args[1]);

                        if (session.isSessionChannelAccessible(msg.getAuthor().getIdLong())) {
                            session.getPlayerListMessage(msg).setTargetChannel(msg.getChannel())
                                    .getScreenshotEmbedAction().queue();
                        }
                        else {
                            msg.getChannel().sendMessage(response).queue();
                            msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions", "**You do not have permissions to view the screenshot!**", EmbedDesign.ERROR).getEmbed()).queue();
                        }
                    }
                    catch (InvalidSessionException e) {
                        mainConfig.dedicatedOutputChannel.sendMessageEmbeds(
                                new MessageEntry("Invalid Session", "**Unable to Find a Session from that search**" +
                                        (!sessionManager.getSessions().isEmpty() ? "\n\n**You may use `" + mainConfig.commandPrefix + "headcount` to see the sessions that are running.**" :
                                                "\n\n**There's no sessions running, this may be because I was just restarted and awaiting the first player list on this instance.**"),
                                        EmbedDesign.ERROR).getEmbed()).queue();
                    }
                    catch (IOException e) {
                        log.error("Unable to send screenshot to {}. Reason: {}",
                                "#" + mainConfig.dedicatedOutputChannel.getName(), e.getMessage());
                        msg.getChannel().asTextChannel().sendMessage(response).queue();
                    }
                }
            }
        }
        else if (sessionManager.usedInSessionChannel(msg)) {
            if (isTeamMember(msg.getAuthor().getIdLong())) {
                try {
                    Session session = sessionManager.getSessionByChannel(msg.getChannel().asTextChannel());

                    session.getPlayerListMessage(msg).setTargetChannel(msg.getChannel()).getScreenshotEmbedAction().queue();
                }
                catch (InvalidSessionException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                msg.getChannel().asTextChannel().sendMessageEmbeds(new MessageEntry("No Permissions", "**You Do Not Have Permissions to use this command in the session channel**",
                        EmbedDesign.ERROR).getEmbed()).queue();
                msg.delete().queue();
            }
        }
        else {
            if (isTeamMember(msg.getAuthor().getIdLong())) {
                if (sessionManager.getSessions().size() == 1) {
                    try {
                        PlayerListMessage playerListMessage = new PlayerListMessage(sessionManager.getSessions().getFirst().getSessionName());
                        playerListMessage.setTargetChannel(msg.getChannel())
                                .getScreenshotEmbedAction().queue();
                    }
                    catch (IOException e) {
                        log.error("Unable to send screenshot to {}. Reason: {}",
                                "#" + mainConfig.dedicatedOutputChannel.getName(), e.getMessage());
                        msg.getChannel().sendMessage(response).queue();
                    }
                    catch (InvalidSessionException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    try {
                        PlayerListMessage playerListMessage = new PlayerListMessage(args[1]);
                        playerListMessage.setTargetChannel(msg.getChannel()).getScreenshotEmbedAction().queue();
                    }
                    catch (InvalidSessionException e) {
                        msg.getChannel().sendMessageEmbeds(
                                new MessageEntry("Invalid Session", "**Unable to Find a Session from no search as there is either more than 1 session running, or no sessions at all.**",
                                        EmbedDesign.ERROR).getEmbed()).queue();
                    }
                    catch (IOException e) {
                        log.error("Unable to send screenshot to {}. Reason: {}",
                                "#" + mainConfig.dedicatedOutputChannel.getName(), e.getMessage());
                        msg.getChannel().sendMessage(response).queue();
                    }
                }
            }
            else {
                msg.delete().queue();
            }
        }
    }



    private MessageCreateAction getHeadCountEmbed(Member cmdUser, MessageChannel channel) {
        List<Session> sessionList = sessionManager.getAccessibleSessions(cmdUser);

        // No Need to Check if cmdUser is null as that's handled by the method that calls this method
        if (sessionList.isEmpty()) {
            return channel.sendMessageEmbeds(new MessageEntry("No Access", "**You Have No Access to Any Sessions**", EmbedDesign.ERROR).getEmbed());
        }

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
                case ONLINE, FRESH_ONLINE ->
                        embedBuilder.addField(currentSession.getSessionName(), "**" + currentSession.getPlayerCount() + " Player" + (currentSession.getPlayerCount() == 1 ? "" : "s") + "**" +

                                (!cmdUser.hasPermission(currentSession.getSessionChannel(), Permission.VIEW_CHANNEL) ? "\n\n:lock: **You Do Not Have Access to this Session**" : ""), false);

                case OFFLINE -> embedBuilder.addField(currentSession.getSessionName(), "*Session is Offline*", false);

                case RESTARTING, RESTART_MOD ->
                        embedBuilder.addField(currentSession.getSessionName(), "*Session is Restarting*", false);

                case RESTART_SOON ->
                        embedBuilder.addField(currentSession.getSessionName(), "*Session is Restarting Soon*", false);
            };

            if (index == sessionList.size() - 1) {
                embedBuilder = embedBuilder.addField("Total", "**" + sessionManager.getSessionTotalPlayerCount() + " Players**", false);
            }

            index++;
        }

        return channel.sendMessageEmbeds(embedBuilder.setThumbnail("attachment://safe-logo.png").build()).setFiles(getSAFECrewLogo());
    }

    public boolean isValidCommand(String cmd) {
        return commands.contains(cmd.toLowerCase());
    }
    private FileUpload getSAFECrewLogo() {
        InputStream resourceStream = getClass().getResourceAsStream("/safe-logo.png");
        FileUpload thumbnail = FileUpload.fromData(resourceStream, "safe-logo.png");

        return thumbnail;
    }
}
