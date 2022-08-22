package Angel.CheckIn.AFKTool;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

class AFKCheck extends ListenerAdapter implements Runnable {
    private final Logger log = LogManager.getLogger(AFKCheck.class);
    private final Member afkChecked;
    private final Member overseeingStaffMember;
    private final TextChannel sessionChannel;
    private Message afkCheckedMessage;
    private final int length;
    private final int mentionOn;
    private int minutes;
    private int seconds;
    private boolean afkCheckRunning = true;
    private boolean successfulCheckIn = false;
    private boolean cancelled = false;
    private final String afkCheckMessage =
            "**$** are you not paying attention to discord? Please respond to this message within the next **%** minutes, " +
                    "otherwise we'll have to assume so and you'll be removed from the session.\n" +
            "\n" +
            ":warning: *Everyone else in the session, **do not tell them they're being AFK checked, otherwise that defeats the point***";

    AFKCheck(Member afkCheckVictim, Member overseeingStaffMember, TextChannel sessionChannel, int length, int mentionOn) {
        this.afkChecked = afkCheckVictim;
        this.overseeingStaffMember = overseeingStaffMember;
        this.sessionChannel = sessionChannel;
        this.length = length;
        this.mentionOn = mentionOn;
        this.minutes = length;
        this.seconds = 0;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.getChannelType().isGuild()) return;
        if (afkChecked.getIdLong() == event.getAuthor().getIdLong() && event.getMessage().getChannel() == sessionChannel) {
            afkCheckRunning = false;
            successfulCheckIn = true;
            afkCheckedMessage = event.getMessage();
            log.info(afkChecked.getEffectiveName() + " successfully completed their AFK check with " + getRemainingTime() + " left on the clock!");
        }
    }

    @Override
    public void run() {
        if (afkCheckRunning) {
            if (minutes == length && seconds == 0) {
                sessionChannel.sendMessage(afkCheckMessage.replace("$", getMemberName()).replace("%", String.valueOf(length))).queue();
                log.info(overseeingStaffMember.getEffectiveName() + " just started an AFK Check on " + getMemberName() + " for " +
                        length + " minutes");
            }

            if (minutes == mentionOn && seconds == 0) {
                sessionChannel.sendMessage(afkCheckMessage.replace("**$**", getMemberMention()).replace("%", String.valueOf(mentionOn))).queue();
                log.info("The " + mentionOn + " minute warning has been given in the session channel for " + afkChecked.getEffectiveName());
            }

            if (minutes == 0 && seconds == 0) {
                afkCheckRunning = false;
                log.info(getMemberName() + " has missed their AFK Check!");
            }
            else if (seconds == 0) {
                log.info(getMemberName() + " has " + minutes-- + " minutes remaining on their AFK check");
                seconds = 59;
            }
            else {
                seconds--;
            }
        }
    }

    void cancelAFKCheck() {
        afkCheckRunning = false;
        cancelled = true;
    }

    String getRemainingTime() {
        if (minutes == 0 && seconds == 0) {
            return "0:00";
        }
        else if (seconds < 10) {
            return minutes + ":0" + seconds;
        }
        else {
            return minutes + ":" + seconds;
        }
    }
    String getMemberName() {
        return afkChecked.getEffectiveName();
    }

    String getMemberMention() {
        return afkChecked.getAsMention();
    }

    Member getAfkCheckVictim() {
        return afkChecked;
    }
    Member getOverseeingStaffMember() {
        return overseeingStaffMember;
    }
    TextChannel getSessionChannel() {
        return sessionChannel;
    }
    boolean isCancelled() {
        return cancelled;
    }
    boolean hasPlayerSuccessfullyCheckedIn() {
        return successfulCheckIn && !afkCheckRunning && !isCancelled();
    }
    boolean hasPlayerFailedCheckIn() {
        return !successfulCheckIn && !afkCheckRunning && !isCancelled();
    }
    Message getCheckInMessage() {
        return afkCheckedMessage;
    }
}