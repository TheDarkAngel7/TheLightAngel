package Angel;

import net.dv8tion.jda.api.EmbedBuilder;

class Help {
    private DiscordBotMain discord;
    private EmbedDesigner embed;
    private EmbedBuilder embedBuilder;
    private String fieldHeader;

    Help(DiscordBotMain importBotInstance) {
        this.discord = importBotInstance;
        this.embed = discord.embed;
        this.embedBuilder = discord.embedBuilder;
        this.fieldHeader = discord.fieldHeader;
    }
    void botAbuseCommand() {
        embed.setAsHelp("/botabuse Command Help");
        embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/botabuse <Mention or Discord ID> <Reason (You'd input a reason key here)> [Image URL]`**\n" +
                "\n" +
                "/botabuse allows you to Bot Abuse the target Mention or Discord ID along with the required reason, " +
                "it’s straight forward on what this command needs. An Image URL argument is required for the most part, but if you’re on mobile," +
                " the bot will accept an image attachment to replace this argument. However, you need to use this command in the " +
                "team discussion channel if you’re going to upload an image attachment due to the command not getting deleted on use.\n\n" +
                "If you're not sure what you'd input for `<Reason>`, being it requires a reason key and not the entire reason spelled out," +
                "use `/reasons` or `/rmgr list` to get what keys direct me to what reasons.", true);
    }
    void permBotAbuseCommand() {
        embed.setAsHelp("/permbotabuse Command Help");
        embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/permbotabuse <Mention or Discord ID> [Image URL]`**\n" +
                "\n" +
                "This allows you to permanently Bot Abuse someone. If they’re currently bot abused, " +
                "the expiry date gets overwritten to being Permanent. " +
                "If they’re not currently bot abused, then it’ll just add a new offense being permanent with the reason “Contact Staff”.", true);
    }
    void undoCommand() {
        embed.setAsHelp("/undo Command Help");
        embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/undo [Mention or Discord ID]`**\n" +
                "\n" +
                "This command is just like hitting the Undo button in word, " +
                "or anywhere else you can think of. " +
                "A “/undo” by itself will undo the last bot abuse that you applied. " +
                "However, including a Mention or Discord ID with the command will undo the bot abuse for that player. " +
                "However this command will only work for Bot Abuses that were issued 5 days or less ago.", true);
    }
    void checkCommand(boolean isTeamMember) {
        embed.setAsHelp("/check Command Help");
        if (isTeamMember) {
            embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/check [TimeZone Offset] <Mention or Discord ID>`**\n" +
                    "\n" +
                    "This command allows you to check on someone’s Bot abuse status. " +
                    "You can put a number in between -12 and 14 in the TimeZone offset argument to convert the times to another time zone, " +
                    "it can accept numbers that are full hour, or a full hour and a half ahead or behind UTC, so 5.5 would be equal to GMT+5:30.)", true);
        }
        else {
            embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/check [dm] [TimeZone]`**\n" +
                    "\n" +
                    "/check allows you to check your own bot abuse status, " +
                    "instead of the staff having to guess when your bot abuse will expire, you can just have the bot tell you.\n\n" +
                    "About `[dm]`: If you would like to opt for TheLightAngel to send you the information via a DM " +
                    "instead of the default location in " + discord.guild.getTextChannelById(discord.botConfig.helpChannel.getIdLong()).getAsMention() + ", you can place a \"dm\" right after the \"/check\".\n\n" +
                    "About `[TimeZone]`: Because I record the times in my own time zone, *what if someone used this command " +
                    "and wanted to see the times in their time zone?* **That's where this argument comes into play!**  " +
                    "Right now the bot is compatible with Time Zones that are a full hour, or a full hour and a half ahead or behind UTC.", true);
        }
    }
    void checkHistoryCommand() {
        embed.setAsHelp("/checkhistory Command Help");
        embedBuilder.addField(fieldHeader, "**Please use `/help check`, " +
                "this command carries the exact same concept and syntax as /check**", true);
    }
    void transferCommand() {
        embed.setAsHelp("/transfer Command Help");
        embedBuilder.addField(fieldHeader, "**Full Syntax:\n `/transfer <Old Mention or Discord ID> <New Mention or Discord ID>`**\n" +
                "\n" +
                "For transferring all records from one discord account to a new discord account. " +
                "This is useful for if someone uses a different discord account to evade being bot abused.\n" +
                "\n" +
                "**Please Note that if the new discord account has existing records, those records will be kept as well.**", true);
    }
    void clearCommand() {
        embed.setAsHelp("/clear Command Help");
        embedBuilder.addField(fieldHeader, "**Full Syntax:\n" +
                "\n" +
                "`/clear <Mention or Discord ID>`**\n" +
                "\n" +
                "Completely wipes out the record of a discord account.",true);
    }
    void reasonManagementCommand() {
        embed.setAsHelp("/reasonsmanager Command Help");
        embedBuilder.addField(fieldHeader, "`/reasonsmanager addreason <newKey> <Full Wording of the Reason>` \n" +
                "\n" +
                "`/reasonsmanager addkeymap <newKey> <existingKey>` \n" +
                "\n" +
                "`/reasonsmanager <remove|del> <existingKey>` \n" +
                "\n" +
                "`/reasonsmanager list`\n" +
                "\n" +
                "**About addreason:** \n" +
                "\n" +
                "Adds a new Shortcut to a reason, whatever you enter into <newKey> is what you would enter into <reason> " +
                "in /botabuse when a bot abuse is needed for someone. \n" +
                "\n" +
                "**About addkeymap:** \n" +
                "\n" +
                "If you would like to create multiple keys to do the same thing, you can use this command to do that. \n" +
                "\n" +
                "**About remove or del:** \n" +
                "If you would like to delete a key and the associated reason then you’d use this command. \n" +
                "**NOTE:** It does not delete any other keys that were mapped to the same reason. \n" +
                "\n" +
                "**About list:** \n" +
                "\n" +
                "This lists all of the keys and the reasons the keys are mapped to.", true);
    }
}