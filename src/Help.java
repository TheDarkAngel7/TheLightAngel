import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import java.awt.*;

class Help {
    private EmbedBuilder embed;
    private String helpIcon = "https://i.imgur.com/saZzlXr.png";

    Help(EmbedBuilder embedBuilder) {
        this.embed = embedBuilder;
    }
    void botAbuseCommand() {
        embed.setTitle("/botabuse Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Full Syntax:\n `/botabuse <Mention or Discord ID> <Reason (“kick”, “offline”, or “staff”. You can also use “k”, “o”, or “s” for short)> [Image URL]`**\n" +
                "\n" +
                "/botabuse allows you to Bot Abuse the target Mention or Discord ID along with the required reason, " +
                "it’s straight forward on what this command needs. However, an Image URL argument is required for the most part but if you’re on mobile for instance," +
                " the bot will accept an image attachment as the image. However, you need to use this command in the " +
                "team discussion channel if you’re going to upload an image attachment due to the command has a 10 second delay before the command gets deleted.", true);
    }
    void permBotAbuseCommand() {
        embed.setTitle("/permbotabuse Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Full Syntax:\n `/permbotabuse <Mention or Discord ID> [Image URL]`**\n" +
                "\n" +
                "This allows you to permanently Bot Abuse someone. If they’re currently bot abused, " +
                "the expiry date gets overwritten to being Permanent. " +
                "If they’re not currently bot abused, then it’ll just add a new offense being permanent with the reason “Contact Staff”.", true);
    }
    void undoCommand() {
        embed.setTitle("/undo Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Full Syntax:\n `/undo [Mention or Discord ID]`**\n" +
                "\n" +
                "This command is just like hitting the Undo button in word, " +
                "or anywhere else you can think of. " +
                "A “/undo” by itself will undo the last bot abuse that you applied. " +
                "However, including a Mention or Discord ID with the command will undo the bot abuse for that player. " +
                "However this command will only work for Bot Abuses that were issued 5 days or less ago.", true);
    }
    void checkCommand(boolean isTeamMember, Guild guild, MessageChannel helpChannel) {
        embed.setTitle("/check Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        if (isTeamMember) {
            embed.addField("System Message", "**Full Syntax:\n `/check [TimeZone Offset] <Mention or Discord ID>`**\n" +
                    "\n" +
                    "This command allows you to check on someone’s Bot abuse status. " +
                    "You can put a number in between -12 and 14 in the TimeZone offset argument to convert the times to another time zone, " +
                    "it can accept numbers that are full hour, or a full hour and a half ahead or behind UTC, so 5.5 would be equal to GMT+5:30.)", true);
        }
        else {
            embed.addField("System Message", "**Full Syntax:\n `/check [dm] [TimeZone]`**\n" +
                    "\n" +
                    "/check allows you to check your own bot abuse status, " +
                    "instead of the staff having to guess when your bot abuse will expire, you can just have the bot tell you.\n\n" +
                    "About `[dm]`: If you would like to opt for TheLightAngel to send you the information via a DM " +
                    "instead of the default location in " + guild.getTextChannelById(helpChannel.getIdLong()).getAsMention() + ", you can place a \"dm\" right after the \"/check\".\n\n" +
                    "About `[TimeZone]`: Because the bot records the times in Central Time/US, *what if someone used this command " +
                    "and wanted to see the times in their own time zone?* **That's where this argument comes into play!**  " +
                    "Right now the bot is compatible with Time Zones that are a full hour, or a full hour and a half ahead or behind UTC.", true);
        }
    }
    void checkHistoryCommand() {
        embed.setTitle("/checkhistory Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Please use `/help check`, " +
                "this command carries the exact same concept and syntax as /check**", true);
    }
    void transferCommand() {
        embed.setTitle("/transfer Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Full Syntax:\n `/transfer <Old Mention or Discord ID> <New Mention or Discord ID>`**\n" +
                "\n" +
                "For transferring all records from one discord account to a new discord account. " +
                "This is useful for if someone uses a different discord account to evade being bot abused.\n" +
                "\n" +
                "**Please Note that if the new discord account has existing records, those records will be kept as well.**", true);
    }
    void clearCommand() {
        embed.setTitle("/clear Command Help");
        embed.setThumbnail(helpIcon);
        embed.setColor(Color.BLUE);
        embed.addField("System Message", "**Full Syntax:\n" +
                "\n" +
                "`/clear <Mention or Discord ID>`**\n" +
                "\n" +
                "Completely wipes out the record of a discord account.",true);
    }
}