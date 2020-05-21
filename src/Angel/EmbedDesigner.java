package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.*;

abstract class EmbedDesigner {
    private EmbedBuilder embed;
    // Image Background Hex: #2F3136
    String checkIcon = "https://i.imgur.com/bakLhaw.png";
    String warningIcon = "https://i.imgur.com/5SD8jxX.png";
    String errorIcon = "https://i.imgur.com/KmZRhnK.png";
    String infoIcon = "https://i.imgur.com/WM8qFWT.png";
    String stopIcon = "https://i.imgur.com/LR6Q5jE.png";
    String helpIcon = "https://i.imgur.com/saZzlXr.png";

    EmbedDesigner(EmbedBuilder importEmbed) {
        embed = importEmbed;
    }
    void setAsSuccess(String title) {
        embed.setColor(Color.GREEN);
        embed.setTitle(title);
        embed.setThumbnail(checkIcon);
    }
    void setAsWarning(String title) {
        embed.setColor(Color.YELLOW);
        embed.setTitle(title);
        embed.setThumbnail(warningIcon);
    }
    void setAsError(String title) {
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(errorIcon);
    }
    void setAsStop(String title) {
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(stopIcon);
    }
    void setAsInfo(String title) {
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(infoIcon);
    }
    void setAsHelp(String title) {
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(helpIcon);
    }
}
