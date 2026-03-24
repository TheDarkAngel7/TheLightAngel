package Angel.PlayerList.HelpRequests;

import Angel.Exceptions.InvalidHelpRequestException;
import Angel.Exceptions.InvalidSessionException;
import Angel.PlayerList.PlayerListLogic;
import Angel.PlayerList.Session;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class HelpRequest implements PlayerListLogic {
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

        boolean playerJoinedMaxHelpersWithFirstArg = false;
        try {
            this.maxHelpers = Integer.parseInt(args[1]);
        }
        catch (NumberFormatException e) {
            try {
                playerJoinedMaxHelpersWithFirstArg = true;
                this.maxHelpers = Integer.parseInt(args[0].substring(args[0].length() - 1));
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
                        targetThread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR).queue();
                        targetThread.join().and(targetThread.addThreadMember(host)).submit()
                                .whenComplete((unused, throwable1) ->
                                        targetThread.sendMessage(
                                                "**Welcome " + host.getAsMention() + " to your own thread channel for your organization! Please use this channel to communicate with your helpers as they join.**" +
                                                "\n\nMax Helpers: **" + this.maxHelpers + "**" +
                                                "\nRequested Help For: **" + this.request + "**" +

                                                        "\n\n**__Commands__**" +
                                                        "\n\n`" + mainConfig.commandPrefix + "startsale`: **This tells me you're starting your sale and you are no longer asking for anymore help. NOTE: Only use this command if you are starting before you have received all of your helpers, this already occurs automatically when you receive all of your helpers.**" +
                                                "\n\n`" + mainConfig.commandPrefix + "requeue` or `" + mainConfig.commandPrefix + "req`: **This tells me you want your request for help requeued. Use this command if you had someone leave the sale or session and you need a replacement.**" +
                                                "\n\n`" + mainConfig.commandPrefix + "helpers <# of Helpers>` (ex. `" + mainConfig.commandPrefix + "helpers 3`): **This tells me how many helpers you're wanting if it is now different from your original request for help.**" +
                                                "\n\n`" + mainConfig.commandPrefix + "purpose <purpose>` (ex. `" + mainConfig.commandPrefix + "purpose 2MCs and bunker`): **This tells me the new purpose for the help request, you should update it before you requeue your request!**" +
                                                "\n\n`" + mainConfig.commandPrefix + "newhost <New Host Mention>` (ex. `" + mainConfig.commandPrefix + "newhost @TheDarkAngel7`): **This tells me that one of the helpers is taking over as host of sales. Use this when you are finished with your sales and going to help one of your helpers.**")
                                                .queue(m -> {
                                                    m.pin().queue();
                                        }));
                    }
                    else aue.logCaughtException(Thread.currentThread(), throwable);
                });
    }

    public void addHelper(Member member) {
        targetThread.addThreadMember(member).submit().thenRun(() -> {

            targetThread.sendMessage("**" + member.getAsMention() + " has joined your sale thread channel!**" +
                    (!receivedAllHelpers() ? "\n\n**Waiting for " + getHelpersToFind() + " more players**" : "")).queue();

            if (receivedAllHelpers()) {
                noLongerWaitingForHelpers();

                String result = "**This sale has received all of its helpers, " +
                        "so it has been removed from the session's sale queue.**" +
                        "\n**Don't Forget to Invite these people to your organization " + host.getAsMention() + "!**" +
                        "\n\nHelpers: ";

                List<Member> helpers = getHelpers();

                int index = 0;

                do {
                    result = result.concat(helpers.get(index).getAsMention());

                    if (index < helpers.size() - 1) {
                        result = result.concat(", ");
                    }
                } while (++index < helpers.size());

                targetThread.sendMessage(result).queue();
            }
        });
    }

    public void removeHelper(Member member) {
        targetThread.removeThreadMember(member).queue();
    }

    public void noLongerWaitingForHelpers() {
        isWaitingForHelpers = false;
    }

    public boolean receivedAllHelpers() {
        return getHelpers().size() >= this.maxHelpers || !isWaitingForHelpers;
    }

    public boolean isWaitingForHelpers() {
        return isWaitingForHelpers;
    }

    public int getHelpersToFind() {
        return maxHelpers - getHelpers().size();
    }

    public int getCurrentNumberOfHelpers() {
        return getHelpers().size();
    }

    public List<Member> getHelpers() {
        List<Member> helpers = new ArrayList<>(targetThread.getMembers());
        int index = 0;
        do {
            if (helpers.get(index).getIdLong() == targetThread.getSelfThreadMember().getIdLong() ||
            helpers.get(index).getIdLong() == host.getIdLong()) {
                helpers.remove(index);
            }
            index++;
        } while (index < helpers.size());

        return helpers;
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
