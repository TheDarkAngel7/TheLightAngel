package Angel;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;

public enum EmbedType {
    EMBED_SUCCESS,
    EMBED_WARNING,
    EMBED_ERROR,
    EMBED_STOP,
    EMBED_INFO,
    EMBED_HELP,
    EMBED_NONE;

    EmbedBuilder getBuilder(EmbedType type, MainConfiguration mainConfig) {
        EmbedBuilder embed = new EmbedBuilder();
        switch (type) {
            case EMBED_SUCCESS:
                embed.setColor(Color.GREEN);
                embed.setThumbnail(mainConfig.checkIconURL);
                break;
            case EMBED_WARNING:
                embed.setColor(Color.YELLOW);
                embed.setThumbnail(mainConfig.warningIconURL);
                break;
            case EMBED_ERROR:
                embed.setColor(Color.RED);
                embed.setThumbnail(mainConfig.errorIconURL);
                break;
            case EMBED_STOP:
                embed.setColor(Color.RED);
                embed.setThumbnail(mainConfig.stopIconURL);
                break;
            case EMBED_INFO:
                embed.setColor(Color.BLUE);
                embed.setThumbnail(mainConfig.infoIconURL);
                break;
            case EMBED_HELP:
                embed.setColor(Color.BLUE);
                embed.setThumbnail(mainConfig.helpIconURL);
                break;
        }
        return embed;
    }
}