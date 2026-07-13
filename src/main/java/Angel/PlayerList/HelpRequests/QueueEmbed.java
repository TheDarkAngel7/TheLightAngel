package Angel.PlayerList.HelpRequests;

import Angel.EmbedDesign;
import Angel.MessageEntry;
import Angel.PlayerList.Exceptions.InvalidSessionException;
import Angel.PlayerList.PlayerListLogic;
import Angel.PlayerList.Session;
import Angel.PlayerList.SessionStatus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class QueueEmbed implements PlayerListLogic {
    private final boolean targetSpecificSession;

    // Can Be Null if Not target Specific
    private final Session targetSession;
    private MessageChannel targetChannel;

    private final Member requester;

    public QueueEmbed(long requester) {
        this.targetSpecificSession = false;
        this.targetSession = null;
        this.requester = getGuild().getMemberById(requester);
    }

    public QueueEmbed(Member requester) {
        this.targetSpecificSession = false;
        this.targetSession = null;
        this.requester = requester;
    }

    public QueueEmbed(MessageChannel channel, Member requester) {
        this.targetSpecificSession = false;
        this.targetSession = null;
        this.targetChannel = channel;
        this.requester = requester;
    }

    public QueueEmbed(String sessionName, Member requester) throws InvalidSessionException {
        this.targetSpecificSession = true;

        // By Default when finding session by name, we target the session object with the designated session channel
        this.targetSession = sessionManager.getSessionByName(sessionName);
        this.targetChannel = targetSession.getSessionChannel();
        this.requester = requester;
    }

    public QueueEmbed(String sessionName, long requester) throws InvalidSessionException {
        this.targetSpecificSession = true;

        this.targetSession = sessionManager.getSessionByName(sessionName);
        this.targetChannel = targetSession.getSessionChannel();
        this.requester = getGuild().getMemberById(requester);
    }

    // Same Logic as the Constructor Above but instead we take the session object rather than getting it from sessionManager
    public QueueEmbed(Session session, Member requester) {
        this.targetSpecificSession = true;

        this.targetSession = session;
        this.targetChannel = targetSession.getSessionChannel();
        this.requester = requester;
    }

    public QueueEmbed(Session session, long targetDiscordID) {
        this.targetSpecificSession = true;

        this.targetSession = session;
        this.targetChannel = targetSession.getSessionChannel();
        this.requester = getGuild().getMemberById(targetDiscordID);
    }

    public void setTargetChannel(MessageChannel channel) {
        this.targetChannel = channel;
    }

    public MessageCreateAction getQueueEmbedAction() {
        InputStream resourceStream;
        FileUpload thumbnail;

        if (!targetSpecificSession) {
            thumbnail = playerListMain.getSAFECrewLogo();
        }
        else {
            resourceStream = getClass().getResourceAsStream("/sessions/" + targetSession.getSessionName().toLowerCase() + "_128sm.png");
            thumbnail = FileUpload.fromData(resourceStream, targetSession.getSessionName().toLowerCase() + "_128sm.png");
        }

        return targetChannel.sendMessageEmbeds(getQueueEmbed()).setFiles(thumbnail);
    }

    private MessageEmbed getQueueEmbed() {
        List<Session> targetSessions = new ArrayList<>();
        EmbedBuilder builder = new EmbedBuilder();

        if (requester == null) {
            return new MessageEntry("No Access", "**You Do Not Have Access to this information**", EmbedDesign.ERROR).getEmbed();
        }

        if (targetSpecificSession) {
            targetSessions.add(targetSession);

            if (!targetSession.isSessionChannelAccessible(requester)) {
                return new MessageEntry("No Access", ":lock: **You Do Not Have Access to this session!**", EmbedDesign.ERROR).getEmbed();
            }

        }
        else {
            targetSessions.addAll(sessionManager.getAccessibleSessions(requester));
        }

        if (targetSessions.size() > 1) {
            builder = builder.setTitle("Total Sale Queue")
                    .setThumbnail("attachment://safe-logo.png");
        }
        else {
            builder = builder.setTitle(targetSession.getSessionName() + "'s Sale Queue (" + targetSession.getSaleQueueSize() + ")")
                    .setThumbnail("attachment://" + targetSession.getSessionName().toLowerCase() + "_128sm.png");
        }

        int sessionsIndex = 0;

        if (!targetSessions.isEmpty()) {

            do {
                Session currentSession = targetSessions.get(sessionsIndex++);

                List<HelpRequest> helpRequests = targetSession.getSaleQueue(true).stream()
                        .filter(hr -> hr.isWaitingForHelpers() || hr.getHelpersToFind() > 0)
                        .toList();
                String queueString = "";

                if (!helpRequests.isEmpty()) {
                    int helpRequestIndex = 0;
                    do {
                        HelpRequest helpRequest = helpRequests.get(helpRequestIndex);

                        // 1st: @Player needs 2 more helpers for 3 MCs and Bunker as of 3 minutes ago.
                        queueString = queueString.concat(
                                "**" + getOrdinalSuffix(currentSession.getQueuePositionByHost(helpRequest.getHost())) + ": " + helpRequest.getHost().getAsMention() + "** needs **"
                                        + helpRequest.getHelpersToFind() + "** more " + (helpRequest.getHelpersToFind() > 1 ? "helpers" : "helper") +  " for **" + helpRequest.getRequest() + "** as of **" + getDiscordRelativeTimeTag(helpRequest.getRequestCreationTime()) + ".**");

                        if (helpRequestIndex < helpRequests.size() - 1) {
                            queueString = queueString.concat("\n\u200B");
                        }
                        if (currentSession.getStatus() != SessionStatus.ONLINE && currentSession.getStatus() != SessionStatus.FRESH_ONLINE) {
                            queueString = "**Session is " + currentSession.getStatus().getStatusString() + "**";
                        }
                    } while (++helpRequestIndex < helpRequests.size());
                }
                else {
                    queueString = "**No Pending Help Requests**";
                }

                builder = builder.addField((targetSessions.size() > 1 ? currentSession.getSessionName() : ""), queueString, false);

            } while (sessionsIndex < targetSessions.size());

            return builder.build();
        }
        else {
            return new MessageEntry("No Active Sessions", "**Apparently I cannot find any active sessions for the help requests. " +
                    "This could be because I was just restarted... or there really is no active sessions.**", EmbedDesign.ERROR).getEmbed();
        }
    }

    private String getOrdinalSuffix(int value) {
        // Handle the 11th, 12th, 13th exception
        if (value % 100 >= 11 && value % 100 <= 13) {
            return value + "th";
        }

        return switch (value % 10) {
            case 1 ->   value + "st";
            case 2 ->   value + "nd";
            case 3 ->   value + "rd";
            default ->  value + "th";
        };
    }
}
