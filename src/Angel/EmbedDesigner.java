package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.*;

abstract class EmbedDesigner {
    private DiscordBotMain discord;
    private EmbedBuilder embed;
    // Image Background Hex: #2F3136
    String checkIcon = "https://i.imgur.com/bakLhaw.png";
    String warningIcon = "https://i.imgur.com/5SD8jxX.png";
    String errorIcon = "https://i.imgur.com/KmZRhnK.png";
    String infoIcon = "https://i.imgur.com/WM8qFWT.png";
    String stopIcon = "https://i.imgur.com/LR6Q5jE.png";
    String helpIcon = "https://i.imgur.com/saZzlXr.png";

    EmbedDesigner(DiscordBotMain importBotInstance) {
        discord = importBotInstance;
        embed = discord.embedBuilder;
    }
    void setAsSuccess(String title) {
        isEmbedReady();
        embed.setColor(Color.GREEN);
        embed.setTitle(title);
        embed.setThumbnail(checkIcon);
    }
    void setAsWarning(String title) {
        isEmbedReady();
        embed.setColor(Color.YELLOW);
        embed.setTitle(title);
        embed.setThumbnail(warningIcon);
    }
    void setAsError(String title) {
        isEmbedReady();
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(errorIcon);
    }
    void setAsStop(String title) {
        isEmbedReady();
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(stopIcon);
    }
    void setAsInfo(String title) {
        isEmbedReady();
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(infoIcon);
    }
    void setAsHelp(String title) {
        isEmbedReady();
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(helpIcon);
    }
    void addMessage(String msg) {
        isEmbedReady();
        while (embed.getFields().isEmpty()) {
            embed.addField(discord.botConfig.fieldHeader, msg, true);
        }
    }
    private void isEmbedReady() {
        if (!embed.getFields().isEmpty()) {
            try {
                discord.threadList.add(Thread.currentThread());
                Thread.sleep(1000000000);
            }
            catch (InterruptedException e) {
                discord.threadList.remove(0);
            }
        }
    }
}
