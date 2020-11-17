package Angel;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;

public enum EmbedDesign {
    SUCCESS,
    WARNING,
    ERROR,
    STOP,
    INFO,
    HELP,
    NONE;

    private MainConfiguration mainConfig;
    // Image Background Hex: #2F3136
    EmbedBuilder getBuilder(EmbedDesign type) {
        EmbedBuilder embed = new EmbedBuilder();
        switch (type) {
            case SUCCESS:
                embed.setColor(Color.GREEN).setThumbnail(mainConfig.checkIconURL);
                break;
            case WARNING:
                embed.setColor(Color.YELLOW).setThumbnail(mainConfig.warningIconURL);
                break;
            case ERROR:
                embed.setColor(Color.RED).setThumbnail(mainConfig.errorIconURL);
                break;
            case STOP:
                embed.setColor(Color.RED).setThumbnail(mainConfig.stopIconURL);
                break;
            case INFO:
                embed.setColor(Color.BLUE).setThumbnail(mainConfig.infoIconURL);
                break;
            case HELP:
                embed.setColor(Color.decode("#2F3136").brighter()).setThumbnail(mainConfig.helpIconURL);
                break;
        }
        return embed;
    }
    void setConfig(MainConfiguration mainConfig) {
        this.mainConfig = mainConfig;
    }
}