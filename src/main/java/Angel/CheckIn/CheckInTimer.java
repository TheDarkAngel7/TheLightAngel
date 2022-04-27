package Angel.CheckIn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

class CheckInTimer extends Timer {
    private final Logger log = LogManager.getLogger(CheckInTimer.class);
    private final CheckInMain ciMain;
    private final CheckInConfiguration ciConfig;
    private int minutes = 0;
    private int seconds = 0;
    private int updateTicker;

    CheckInTimer(CheckInMain ciMain, CheckInConfiguration ciConfig) {
        this.ciMain = ciMain;
        this.ciConfig = ciConfig;
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