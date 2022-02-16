package Angel.CheckIn;

import java.util.Timer;
import java.util.TimerTask;

class CheckInTimer extends Timer {
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
        minutes = ciConfig.getCheckInDuration();
        updateTicker = 1;
        this.schedule(new TimerTask() {
            @Override
            public void run() {
                if (updateTicker++ == ciConfig.getCheckInUpdate()) {
                    ciMain.sendCheckInProgressEmbed(null, true);
                    ciMain.sendSessionChannelMessage(true);
                    updateTicker = 1;
                }

                if (minutes == 0 && seconds == 0) {
                    ciMain.endCheckIn();
                    this.cancel();
                }
                else if (seconds == 0) {
                    minutes--;
                    seconds = 59;
                }
                else {
                    seconds--;
                }
            }
        },0, 1000);
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