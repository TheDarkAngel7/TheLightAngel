package Angel.PlayerList.HelpRequests;

import Angel.BotAbuse.BotAbuseLogic;
import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import Angel.PlayerList.PlayerListLogic;
import Angel.PlayerList.Session;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.thread.member.ThreadMemberLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HelpRequestMain extends ListenerAdapter implements BotAbuseLogic, PlayerListLogic {
    private final Logger log = LogManager.getLogger(HelpRequestMain.class);

    @Override
    public void onThreadMemberLeave(@NotNull ThreadMemberLeaveEvent event) {
        if (isValidSaleThreadChannel(event.getThread()) && !event.getThread().isArchived() && !event.getThread().isLocked()) {
            try {
                Session session = sessionManager.getSessionByName(event.getThread().getParentChannel().getName());
                HelpRequest helpRequest = session.getHelpRequestByHost(event.getMember());

                if (event.getMember().getIdLong() == helpRequest.getHost().getIdLong()) {
                    session.closeHelpRequest(event.getMember(), "Host Left Thread Channel");
                }
                else {
                    event.getThread().sendMessage(event.getThreadMember().getMember().getEffectiveName() + " has left the thread." +
                            "\n\n**If you need another helper, be sure to update the purpose with the sales that you still need to do (see pinned message), " +
                            "and then you can use `" + mainConfig.commandPrefix + "requeue`**" +
                            "\n\n**Otherwise if you're finished with your sales and nobody has sales to do, just use `" + mainConfig.commandPrefix + "close`**").queue();
                }
            }
            catch (InvalidSessionException e) {
                throw new RuntimeException(e);
            }


        }

        if (event.getThread().getMemberCount() == 1) {
            event.getThread().getManager().setLocked(true).setArchived(true)
                    .and(event.getThread().leave()).queue();
        }
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        List<Role> removedRoles = event.getRoles();

        for (Role role : removedRoles) {
            if (role.getIdLong() == mainConfig.getMemberRole().getIdLong()) {
                sessionManager.getSessions().forEach(s -> s.closeHelpRequest(event.getMember(), "Host has lost access to this session"));
            }
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        sessionManager.getSessions().forEach(s -> {
            s.closeHelpRequest(event.getUser().getIdLong(), "Host has left the server");
        });
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        Message originalMessage = event.getChannel().getHistory().getMessageById(event.getMessageIdLong());

        if (event.getReaction().getEmoji().getName().equalsIgnoreCase("inv") && playerListMain.usedInSessionChannel(originalMessage)) {
            inviteCmd(originalMessage);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getAuthor().isBot()) return;

        Message msg =  event.getMessage();
        String[] args;

        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - This exception is already handed by DiscordBotMain
            return;
        }

        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {

            if (msg.getChannel().getType() == ChannelType.GUILD_PRIVATE_THREAD) {

                switch (args[0].toLowerCase()) {
                    case "start":
                    case "startsale":

                    case "req":
                    case "requeue":

                    case "helper":
                    case "helpers":

                    case "purpose":

                    case "newhost":

                    case "close":
                    case "closed":
                    case "closesale":

                }
            }
            else {
                switch (args[0].toLowerCase()) {
                    case "lf":
                    case "lfp":
                        lookingForCmd(msg);
                        break;
                    case "inv":
                    case "join":
                        inviteCmd(msg);
                        break;
                    case "queue":
                    case "q":
                        queueCmd(msg);
                        break;
                }
            }
        }
        else if (msg.getContentRaw().toLowerCase().split(" ")[0].contains("lf")) {
            lookingForCmd(msg);
        }
        else if (msg.getContentRaw().toLowerCase().split(" ")[0].contains("inv") && playerListMain.usedInSessionChannel(msg)) {
            inviteCmd(msg);
        }
    }

    private void lookingForCmd(Message msg) {
        try {
            Session session = sessionManager.getSessionByChannel(msg.getChannel().asTextChannel());

            session.createNewHelpRequest(msg);
            /* For Testing Purposes this has been noted out

            if (session.isPlayerInSession(msg.getMember())) {
                session.createNewHelpRequest(msg);
            }
            else {
                msg.replyEmbeds(new MessageEntry("Player Not Found", "**It seems I didn't find you in the session...**" +
                        "\n\n**If this is incorrect, then there is a possibility it's just a misread of the on-screen recognition. " +
                        "If this issue persists then try changing your username to something that's more recognizable by the on-screen recognition.**", EmbedDesign.ERROR).getEmbed()).queue();
            }
             */

        }
        catch (InvalidSessionException ex) {
            // Command Wasn't used in a session channel
            if (msg.getChannel().getName().contains("lf")) {
                msg.getChannel().asTextChannel().createThreadChannel(msg.getMember().getEffectiveName() + " Session", false)
                        .queue(tc -> {
                            tc.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
                        });
            }
            else {
                msg.replyEmbeds(new MessageEntry("No Permissions", "**No Permissions to Create a Help Request here**", EmbedDesign.ERROR).getEmbed()).queue();
            }
        }
    }

    private void inviteCmd(Message msg) {
        HelpRequest helpRequest;
        Session session;
        try {
            session = sessionManager.getSessionByChannel(msg.getChannel().asTextChannel());
        }
        catch (InvalidSessionException e) {
            throw new RuntimeException(e);
        }

        try {
            helpRequest = session.getHelpRequestByHost(msg.getReferencedMessage().getMember());

            if (!helpRequest.receivedAllHelpers()) {
                helpRequest.addHelper(msg.getMember());
            }
            else {
                msg.replyEmbeds(new MessageEntry("Sale Full", "**Unable to join this sale as they have already received their helpers.**", EmbedDesign.ERROR).getEmbed()).queue();
                return;
            }

        }
        catch (NullPointerException ex) {
            List<Member> mentionedPlayers = msg.getMentions().getMembers();

            if (mentionedPlayers.size() == 1) {
                helpRequest = session.getHelpRequestByHost(mentionedPlayers.getFirst());

                helpRequest.addHelper(msg.getMember());
            }
            else {
                msg.replyEmbeds(new MessageEntry("No Sale Found", "**Unable to Find a sale from that phrase.**" +
                        "\nRemember, you need to either reply to the original LF message by the host or you can also use ` " + mainConfig.commandPrefix + "inv <@mention>`", EmbedDesign.ERROR).getEmbed()).queue();
                return;
            }
        }

        HelpRequest oldHelpRequest = session.getHelpRequestByHost(msg.getMember());

        if (oldHelpRequest != null) {
            session.closeHelpRequest(oldHelpRequest, "Host Left to Help Someone Else");
        }
    }

    private void queueCmd(Message msg) {
        String[] args = msg.getContentRaw().substring(1).toLowerCase().split(" ");

        if (playerListMain.usedInSessionChannel(msg)) {
            // !queue
            if (args.length == 1) {
                try {
                    QueueEmbed queueEmbed = new QueueEmbed(sessionManager.getSessionByChannel(msg.getChannel().asTextChannel()), msg.getMember());

                    if (baCore.botAbuseIsCurrent(msg.getAuthor().getIdLong())) {

                        queueEmbed = queueEmbed.setTargetChannel(mainConfig.dedicatedOutputChannel);

                        mainConfig.dedicatedOutputChannel.sendMessage("**Unfortunately you are bot abused " + msg.getAuthor().getAsMention() + ", so your queue embed has been redirected here:**").queue();
                    }

                    queueEmbed.getQueueEmbedAction().queue();
                }
                catch (InvalidSessionException e) {
                    throw new RuntimeException(e);
                }
            }
            // !queue <session> in a Session Channel
            else {
                try {
                    Session session = sessionManager.getSessionByName(args[1]);
                    QueueEmbed queueEmbed = new QueueEmbed(session, msg.getMember());

                    if (isTeamMember(msg.getMember()) || msg.getChannel().getIdLong() == session.getSessionChannel().getIdLong()) {
                        queueEmbed = queueEmbed.setTargetChannel(msg.getChannel());
                    }

                    else {
                        queueEmbed = queueEmbed.setTargetChannel(mainConfig.dedicatedOutputChannel);
                        msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions",
                                        "**I cannot print that out here but I will print it out in " + mainConfig.dedicatedOutputChannel.getAsMention() + "**", EmbedDesign.ERROR).getEmbed())
                                .queue(m -> {
                                    msg.delete().queueAfter(30, TimeUnit.SECONDS);
                                    m.delete().queueAfter(30, TimeUnit.SECONDS);
                                });
                    }
                    queueEmbed.getQueueEmbedAction().queue();
                }
                catch (InvalidSessionException e) {
                    msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session",
                                    "**It seems you didn't provide me a valid session name, but if I were you I would go to " + mainConfig.dedicatedOutputChannel.getAsMention() + " for that**", EmbedDesign.ERROR).getEmbed())
                            .queue(m -> {
                                msg.delete().queueAfter(30, TimeUnit.SECONDS);
                                m.delete().queueAfter(30, TimeUnit.SECONDS);
                            });
                }
            }
        }
        else if (msg.getChannel().getType().isThread() && sessionManager.isParentChannelASessionChannel(msg.getChannel().asThreadChannel())) {

            try {
                QueueEmbed queueEmbed;
                if (args.length == 1) {
                    queueEmbed = new QueueEmbed(msg.getChannel().asThreadChannel().getParentChannel().getName(), msg.getMember()).setTargetChannel(msg.getChannel());
                }
                else {
                    queueEmbed = new QueueEmbed(args[1], msg.getMember()).setTargetChannel(msg.getChannel());
                }
                queueEmbed.getQueueEmbedAction().setMessageReference(msg).mentionRepliedUser(false).queue();
            }
            catch (InvalidSessionException e) {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session",
                                "**It seems you didn't provide me a valid session name, but if I were you I would go to " + mainConfig.dedicatedOutputChannel.getAsMention() + " for that**", EmbedDesign.ERROR).getEmbed())
                        .queue(m -> {
                            msg.delete().queueAfter(30, TimeUnit.SECONDS);
                            m.delete().queueAfter(30, TimeUnit.SECONDS);
                        });
            }


        }
        else {
            List<Session> accessibleSessions = sessionManager.getAccessibleSessions(msg.getAuthor().getIdLong());
            QueueEmbed queueEmbed;

            if (accessibleSessions.isEmpty()) {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("No Permissions", "**You Do Not Have Permissions to view this information!**",
                        EmbedDesign.ERROR).getEmbed()).queue();
                return;
            }

            if (args.length == 1) {

                if (accessibleSessions.size() == 1) {
                    try {
                        queueEmbed = new QueueEmbed(accessibleSessions.getFirst().getSessionName(),  msg.getAuthor().getIdLong());
                    }
                    catch (InvalidSessionException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    queueEmbed = new QueueEmbed(msg.getAuthor().getIdLong());
                }


            }
            else if (args.length == 2) {
                try {
                    queueEmbed = new QueueEmbed(args[1], msg.getAuthor().getIdLong());
                }
                catch (InvalidSessionException e) {
                    msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Session", "**Whoops... this does not appear to belong to a session that's currently running!**" +
                                    "\n\n**You may use `" + mainConfig.commandPrefix + "headcount` to see what sessions are available.**", EmbedDesign.ERROR).getEmbed())
                            .queue();
                    return;
                }
            }
            else {
                msg.getChannel().sendMessageEmbeds(new MessageEntry("Invalid Arguments", "**Whoops... I was expecting just 1 argument... but found " + (args.length - 1) + "**", EmbedDesign.ERROR).getEmbed())
                        .queue(m -> {
                            m.delete().queueAfter(10, TimeUnit.SECONDS);
                            msg.delete().queueAfter(10, TimeUnit.SECONDS);
                        });
                return;
            }

            if (!isTeamMember(msg.getAuthor().getIdLong()) && msg.getChannel().getIdLong() != mainConfig.dedicatedOutputChannel.getIdLong() && msg.getChannelType() != ChannelType.PRIVATE) {

                mainConfig.dedicatedOutputChannel.sendMessage(msg.getAuthor().getAsMention()).queue();

                queueEmbed = queueEmbed.setTargetChannel(mainConfig.dedicatedOutputChannel);
            }
            queueEmbed.getQueueEmbedAction().queue();
        }
    }

    private boolean isValidSaleThreadChannel(ThreadChannel tc) {
        List<HelpRequest> helpRequests = new ArrayList<>();
        List<Session> sessions = sessionManager.getSessions();

        int index = 0;

        do {
            helpRequests.addAll(sessions.get(index++).getHelpRequests());
        } while (index < sessions.size());

        index = 0;

        do {
            if (helpRequests.get(index++).getThreadChannel().getIdLong() == tc.getIdLong()) {
                return true;
            }
        } while (index < helpRequests.size());

        return false;
    }
}
