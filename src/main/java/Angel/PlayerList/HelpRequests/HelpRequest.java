package Angel.PlayerList.HelpRequests;

import Angel.PlayerList.Exceptions.InvalidHelpRequestException;
import Angel.PlayerList.Exceptions.InvalidSessionException;
import Angel.PlayerList.PlayerListLogic;
import Angel.PlayerList.Session;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelpRequest implements PlayerListLogic {
    private final Logger log = LogManager.getLogger(HelpRequest.class);

    private Session session = null;

    private ThreadChannel targetThread;

    private Member host;
    private int maxHelpers;
    private String request;

    private boolean isWaitingForHelpers = true;

    private ZonedDateTime requestCreationTime = ZonedDateTime.now();

    public HelpRequest(Message cmd) throws InvalidHelpRequestException {
        String[] args = cmd.getContentRaw().substring(1).split(" ");
        this.host = cmd.getMember();

        /*
        First we're going to try to get the max helpers from the command or help message:
        If a player says "lf3 Bunker" or "lfp3 Bunker" this would cause NumberFormatException to be thrown
        so then we try to grab the number from the last character of the first argument args[0]
         */

        boolean playerJoinedMaxHelpersWithFirstArg = true;
        try {
            if (args[0].charAt(args[0].length() - 1) == 'm') {
                this.maxHelpers = Integer.parseInt(args[0].substring(args[0].length() - 2));
            }
            else {
                this.maxHelpers = Integer.parseInt(args[0].substring(args[0].length() - 1));
            }
        }
        catch (NumberFormatException e) {
            try {
                playerJoinedMaxHelpersWithFirstArg = false;
                this.maxHelpers = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException ex) {
                throw new InvalidHelpRequestException(cmd.getChannel().asTextChannel(), "Invalid Number of Helpers");
            }
        }
        String result = "";

        int index = 2;
        if (playerJoinedMaxHelpersWithFirstArg) index = 1;


        do {
            result = result.concat(args[index++] + " ");
        }
        while (index < args.length);

        this.request = result;

        try {
            this.session = sessionManager.getSessionByChannel(cmd.getChannel().asTextChannel());

            createNewThreadChannel(session.getSessionChannel());
        }
        catch (InvalidSessionException e) {
            if (cmd.getChannel().getName().contains("lfp")) {
                createNewThreadChannel(cmd.getChannel().asTextChannel());
            }
            else {
                throw new InvalidHelpRequestException(cmd.getChannel().asTextChannel(), "No Permissions");
            }
        }
    }

    private void createNewThreadChannel(TextChannel channel) {
        channel.createThreadChannel(host.getEffectiveName() + ": " + request, true).submit()
                .whenComplete((thread, throwable) -> {
                    if (throwable == null) {
                        this.targetThread = thread;
                        targetThread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR).queue(
                                success -> log.debug("Successfully Setup Thread Channel for {} with an Auto Archive Duration of 1 Hour", host.getEffectiveName()),
                                error -> log.error("Failed to Setup Thread Channel for {}", host.getEffectiveName(), error)
                        );
                        targetThread.join().and(targetThread.addThreadMember(host)).submit()
                                .thenAccept(unused -> {
                                    log.info("Successfully Created New Thread Channel for {}, Purpose: {}", host.getEffectiveName(), request);
                                    targetThread.sendMessage(
                                                    "**Welcome " + host.getAsMention() + " to your own thread channel for your organization! Please use this channel to communicate with your helpers as they join.**" +
                                                            "\n\nMax Helpers: **" + this.maxHelpers + "**" +
                                                            "\nRequested Help For: **" + this.request + "**" +

                                                            "\n\n**__Commands__**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "startsale`: **This tells me you're starting your sale and you are no longer asking for anymore help. NOTE: Only use this command if you are starting before you have received all of your helpers, this already occurs automatically when you receive all of your helpers.**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "requeue` or `" + mainConfig.commandPrefix + "req`: **This tells me you want your request for help requeued. Use this command if you had someone leave the sale or session and you need a replacement.**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "helpers <# of Helpers>` (ex. `" + mainConfig.commandPrefix + "helpers 3`): **This tells me how many helpers you're wanting if it is now different from your original request for help.**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "purpose <purpose>` (ex. `" + mainConfig.commandPrefix + "purpose 2MCs and bunker`): **This tells me the new purpose for the help request, you should update it before you requeue your request!**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "newhost <New Host Mention>` (ex. `" + mainConfig.commandPrefix + "newhost @TheDarkAngel7`): **This tells me that one of the helpers is taking over as host of sales. Use this when you are finished with your sales and going to help one of your helpers.**" +
                                                            "\n\n`" + mainConfig.commandPrefix + "close` **This tells me you're done with this thread and I can close it, you may use this at the end of your sale and nobody else has sales, or if you created this thread in error.**")
                                            .submit().thenAccept(m -> m.pin().queue(
                                                    success -> log.debug("Successfully Pinned the Getting Started Message to Thread Channel!"),
                                                error -> log.error("Unable to Pin the Getting Started Message to Thread Channel", error)
                                            ));
                                        });
                    }
                    else {
                        log.error("Unable to Create New Thread Channel for {}", host.getEffectiveName(), throwable);
                    }
                });
    }

    public void addHelper(Member member, boolean addMemberToThread) {

        if (addMemberToThread) {
            targetThread.addThreadMember(member).queue(success -> {

                        targetThread.sendMessage("**" + member.getAsMention() + " has joined your sale thread channel!**" +
                                (!receivedAllHelpers() ? "\n\n**Waiting for " + getHelpersToFind() + " more " + (getHelpersToFind() > 1 ? "players" : "player") + "...**" +
                                                         "\n**Remember " + host.getAsMention() + ", if you wish to start your sale and you don't want to wait for any more helpers," +
                                                         " use `" + mainConfig.commandPrefix + "startsale` in this channel to remove the sale from the waiting queue, then you may start your sale in game.**": "")).queue();

                        log.info("{} has joined as a Helper for {}'s sale thread. Helper count: {}", member.getEffectiveName(), host.getEffectiveName(), getCurrentNumberOfHelpers());

                    },
                    error -> log.error("{} was unable to join {} sale thread", member.getEffectiveName(), host.getEffectiveName(), error));
        }

        if (receivedAllHelpers()) {
            noLongerWaitingForHelpers();
        }
    }

    public void kickHelper(Member member) {
        targetThread.removeThreadMember(member).queue(
                success -> log.info("{} was kicked from {}'s sale channel", member.getEffectiveName(), host.getEffectiveName()),
                error -> log.error("Unable to kick {} from {}'s sale channel", member.getEffectiveName(), host.getEffectiveName(), error)
                );
    }

    public void setNewHost(Member m) {
        if (!getHelpers().contains(m)) {
            targetThread.addThreadMember(m).queue();
        }
        host = m;

        targetThread.sendMessage("**" + host.getAsMention() + " was successfully set as the new host for the sale!**").queue();

        log.debug("[Thread ID: {}] New Host is now {} ", targetThread.getIdLong(), host.getEffectiveName());

        updateThreadTitle();
    }

    public void setNewPurpose(String purpose) {
        this.request = purpose;
        targetThread.sendMessage("The purpose of this sale is now **" + purpose + "**").queue();

        log.debug("[Thread ID: {}] {}'s new sale thread purpose is \"{}\"", targetThread.getIdLong(), host.getEffectiveName(), purpose);

        updateThreadTitle();
    }

    private void updateThreadTitle() {
        targetThread.getManager().setName(host.getEffectiveName() + ": " + request).queue();
    }

    public void setNewMaxPlayers(int newMaxPlayers) {
        this.maxHelpers = newMaxPlayers;
        targetThread.sendMessage("The New Maximum Number of Helpers is now **" + maxHelpers + "**").queue();

        log.debug("[Thread ID: {}] The new maximum number of helpers is now {} for {}'s sale", targetThread.getIdLong(), maxHelpers, host.getEffectiveName());
    }

    public void noLongerWaitingForHelpers() {
        isWaitingForHelpers = false;

        String result = "**This sale has received all of its helpers, " +
                "so it has been removed from the session's sale queue.**" +
                "\n**Don't Forget to Invite these people to your organization " + host.getAsMention() + "!**" +
                "\n\nHelpers: **";

        List<Member> helpers = getHelpers();

        int index = 0;

        do {
            result = result.concat(helpers.get(index).getAsMention());

            if (index < helpers.size() - 1) {
                result = result.concat(", ");
            }
        } while (++index < helpers.size());

        targetThread.sendMessage(result.concat("**" +
                "\n\n**Players can no longer join this thread via commands. You may still manually add players to this thread by `@Mention` them here.**" +
                "\n**When you're done with this thread you may also use `" + mainConfig.commandPrefix + "close` when this channel is no longer needed**")).queue();
    }

    public void requeueRequest() {
        requestCreationTime = ZonedDateTime.now();
        isWaitingForHelpers = true;
        targetThread.sendMessage("**The sale has been requeued!**" +
                "\n\nHost: **" + host.getAsMention() + "**" +
                "\nPurpose: **" + request + "**" +
                "\nHelpers Needed: **" + getHelpersToFind() + "**").queue();
        session.getSessionChannel().sendMessage("**" + host.getEffectiveName() + " has reentered the queue as they need " + getHelpersToFind() + " more " +
                (getHelpersToFind() > 1 ? "helpers" : "helper") + " for " + request + "**" +
                "\n\nQueue Position: **" + session.getQueuePositionByHost(host) + "**").queue();
    }

    public boolean receivedAllHelpers() {
        return getHelpers().size() >= this.maxHelpers || !isWaitingForHelpers;
    }

    public boolean isWaitingForHelpers() {
        return isWaitingForHelpers && getHelpersToFind() > 0;
    }

    public int getHelpersToFind() {
        return maxHelpers - getHelpers().size();
    }

    public int getCurrentNumberOfHelpers() {
        return getHelpers().size();
    }

    public List<Member> getHelpers() {
        List<ThreadMember> helpers = new ArrayList<>();

        try {
            helpers.addAll(targetThread.getThreadMembers());
        }
        catch (NullPointerException e) {
            return Collections.emptyList();
        }

        int index = 0;
        while (index < helpers.size()) {
            if (helpers.get(index).getIdLong() == targetThread.getOwnerIdLong() ||
            helpers.get(index).getIdLong() == host.getIdLong()) {
                helpers.remove(index);
                index = -1;
            }
            index++;
        }

        return helpers.stream().map(ThreadMember::getMember).toList();
    }



    public ThreadChannel getThreadChannel() {
        return targetThread;
    }

    public Member getHost() {
        return host;
    }

    public int getMaxHelpers() {
        return maxHelpers;
    }

    public String getRequest() {
        return request;
    }

    public ZonedDateTime getRequestCreationTime() {
        return requestCreationTime;
    }
}
