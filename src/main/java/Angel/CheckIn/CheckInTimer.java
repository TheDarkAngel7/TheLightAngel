package Angel.CheckIn;

import Angel.EmbedDesign;
import Angel.MainConfig;
import Angel.MessageEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

class CheckInTimer extends Timer implements MainConfig {
    private final Logger log = LogManager.getLogger(CheckInTimer.class);
    private final CheckInMain ciMain;
    private final CheckInConfiguration ciConfig;
    private int minutes = 0;
    private int seconds = 0;
    private int mentionOn;
    private boolean mentionEnabled;
    private int updateTicker;

    CheckInTimer(CheckInMain ciMain, CheckInConfiguration ciConfig) {
        this.ciMain = ciMain;
        this.ciConfig = ciConfig;
        mentionOn = ciConfig.getWhenMentionCheckInRole();
        mentionEnabled = mentionOn > 0;
    }

    void startTimer() {
        log.info("Check-In Timer Starting...");
        minutes = ciConfig.getCheckInDuration();
        updateTicker = 1;
        this.schedule(new TimerTask() {
            @Override
            public void run() {
                if (updateTicker++ == ciConfig.getCheckInUpdate()) {
                    try {
                        ciMain.sendCheckInProgressEmbed(null, true);
                        ciMain.sendSessionChannelMessage(true);
                        updateTicker = 1;
                    }
                    catch (NullPointerException ex) {}
                }

                if (minutes == mentionOn && seconds == 0 && mentionEnabled) {
                    log.info("There is " + mentionOn + " minutes remaining in the Check-In... Sending " + mentionOn + " minute warning!");
                    ciConfig.getCheckInChannel().sendMessage(ciConfig.getCheckInRole().getAsMention()).queue();
                    ciConfig.getCheckInChannel().sendMessageEmbeds(new MessageEntry(mentionOn + " Minutes Remaining!",
                            ":warning: **There is " + mentionOn + " minutes remaining on the Check-In!\n " +
                                    "Please respond with `"+ mainConfig.commandPrefix + "checkin` as soon as possible to prove you are paying attention to discord!**" +
                                    "\n\n**Once you check-in I will send you a receipt that proves you did.**",
                            EmbedDesign.WARNING).dontUseFieldHeader().getEmbed()).queue();
                }

                if (minutes == 0 && seconds == 0) {
                    log.info("Time is Up on the Check-In! Ending...");
                    ciMain.endCheckIn();
                    this.cancel();
                }
                else if (seconds == 0) {
                    log.info("Check-In Running: " + minutes + " minutes left!");
                    minutes--;
                    seconds = 59;
                }
                else {
                    seconds--;
                }
            }
        },0, 1000);
    }

    void stopTimer() {
        this.cancel();
        log.info("Check-In Timer was successfully stopped and purged with stop code: " + this.purge());
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
}