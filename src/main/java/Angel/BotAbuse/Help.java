package Angel.BotAbuse;

import Angel.EmbedHandler;
import Angel.MainConfiguration;
import net.dv8tion.jda.api.entities.TextChannel;

class Help {
    private BotAbuseMain baMain;
    private EmbedHandler embed;
    private MainConfiguration mainConfig;

    public Help(BotAbuseMain baMain, EmbedHandler embed, MainConfiguration mainConfig) {
        this.baMain = baMain;
        this.embed = embed;
        this.mainConfig = mainConfig;
    }

    void botAbuseCommand(boolean isTeamMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "botabuse Command Help",
        "**Full Syntax:\n `" + mainConfig.commandPrefix + "botabuse <Mention or Discord ID> <Reason (You'd input a reason key here)> [Image URL]`**\n" +
                "\n" +
                mainConfig.commandPrefix + "botabuse allows you to Bot Abuse the target Mention or Discord ID along with the required reason, " +
                "it’s straight forward on what this command needs. An Image URL argument is required for the most part, " +
                "but the bot will accept an image attachment to the command to replace this argument. " +
                "\nFor example, if you upload a picture from your phone as your Bot Abuse evidence, in the Comments (Optional) you'd insert the command." +
                "However, you need to use this command in the " +
                "team discussion channel if you’re going to upload an image attachment due to the command not getting deleted on use.\n\n" +
                "If you're not sure what you'd input for `<Reason>`, being it requires a reason key and not the entire reason spelled out," +
                "use `" + mainConfig.commandPrefix + "reasons` or `" + mainConfig.commandPrefix + "rmgr list` to get what keys direct me to what reasons.");
        if (!isTeamMember) noPermissions();
    }
    void permBotAbuseCommand(boolean isStaffMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "permbotabuse Command Help",
        "**Full Syntax:\n `" + mainConfig.commandPrefix + "permbotabuse <Mention or Discord ID> [Image URL]`**\n" +
                "\n" +
                "This allows you to permanently Bot Abuse someone. If they’re currently bot abused, " +
                "the expiry date gets overwritten to being Permanent. " +
                "If they’re not currently bot abused, then it’ll just add a new offense being permanent with the reason \"Contact SAFE Team\".");
        if (!isStaffMember) noPermissions();
    }
    void undoCommand(boolean isTeamMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "undo Command Help",
        "**Full Syntax:\n `" + mainConfig.commandPrefix + "undo [Mention or Discord ID]`**\n" +
                "\n" +
                "This command is just like hitting the Undo button in word, " +
                "or anywhere else you can think of. " +
                "A “" + mainConfig.commandPrefix + "undo” by itself will undo the last bot abuse that you applied. " +
                "However, including a Mention or Discord ID with the command will undo the bot abuse for that player. " +
                "However this command will only work for Bot Abuses that were issued 5 days or less ago.");
        if (!isTeamMember) noPermissions();
    }
    void checkCommand(boolean isTeamMember) {
        String defaultTitle = mainConfig.commandPrefix + "check Command Help";
        TextChannel outputChannel = null;
        if (mainConfig.forceToDedicatedChannel) outputChannel = mainConfig.dedicatedOutputChannel;
        else outputChannel = mainConfig.helpChannel;
        if (isTeamMember) {
            embed.setAsHelp(defaultTitle,"**Full Syntax:\n `" + mainConfig.commandPrefix + "check [TimeZone Offset] <Mention or Discord ID>`**\n" +
                    "\n" +
                    "This command allows you to check on someone’s Bot abuse status. " +
                    "You can put a number in between -12 and 14 in the TimeZone offset argument to convert the times to another time zone, " +
                    "it can accept numbers that are full hour, or a full hour and a half ahead or behind UTC, so 5.5 would be equal to GMT+5:30.)");
        }
        else {
            embed.setAsHelp(defaultTitle,"**Full Syntax:\n `" + mainConfig.commandPrefix + "check [dm] [TimeZone]`**\n" +
                    "\n" +
                    mainConfig.commandPrefix + "check allows you to check your own bot abuse status, " +
                    "instead of the staff having to guess when your bot abuse will expire, you can just have the bot tell you.\n\n" +
                    "About `[dm]`: If you would like to opt for TheLightAngel to send you the information via a DM " +
                    "instead of the default location in " + outputChannel.getAsMention() + ", you can place a \"dm\" right after the " +
                    "\"" + mainConfig.commandPrefix + "check\".\n\n" +
                    "About `[TimeZone]`: Because I record the times in my own time zone, *what if someone used this command " +
                    "and wanted to see the times in their time zone?* **That's where this argument comes into play!**  " +
                    "Right now the bot is compatible with Time Zones that are a full hour, or a full hour and a half ahead or behind UTC.");
        }
    }
    void checkHistoryCommand() {
        embed.setAsHelp(mainConfig.commandPrefix + "checkhistory Command Help",
        "**Please use `" + mainConfig.commandPrefix + "help check`, " +
                "this command carries the exact same concept and syntax as " + mainConfig.commandPrefix + "check**");
    }
    void transferCommand(boolean isStaffMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "transfer Command Help",
        "**Full Syntax:\n `" + mainConfig.commandPrefix + "transfer <Old Mention or Discord ID> <New Mention or Discord ID>`**\n" +
                "\n" +
                "For transferring all records from one discord account to a new discord account. " +
                "This is useful for if someone uses a different discord account to evade being bot abused.\n" +
                "\n" +
                "**Please Note that if the new discord account has existing records, those records will be kept as well.**");
        if (!isStaffMember) noPermissions();
    }
    void clearCommand(boolean isStaffMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "clear Command Help",
        "**Full Syntax:\n" +
                "\n" +
                "`" + mainConfig.commandPrefix + "clear <Mention or Discord ID>`**\n" +
                "\n" +
                "Completely wipes out the record of a discord account.");
        if (!isStaffMember) noPermissions();
    }
    void reasonManagementCommand(boolean isTeamMember) {
        embed.setAsHelp(mainConfig.commandPrefix + "reasonsmanager Command Help",
        "`" + mainConfig.commandPrefix + "reasonsmanager addreason <newKey> <Full Wording of the Reason>` \n" +
                "\n" +
                "`" + mainConfig.commandPrefix + "reasonsmanager addkeymap <newKey> <existingKey>` \n" +
                "\n" +
                "`" + mainConfig.commandPrefix + "reasonsmanager <remove|del> <existingKey>` \n" +
                "\n" +
                "`" + mainConfig.commandPrefix + "reasonsmanager list`\n" +
                "\n" +
                "**About addreason:** \n" +
                "\n" +
                "Adds a new Shortcut to a reason, whatever you enter into <newKey> is what you would enter into <reason> " +
                "in " + mainConfig.commandPrefix + "botabuse when a bot abuse is needed for someone. \n" +
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
                "This lists all of the keys and the reasons the keys are mapped to.");
        if (!isTeamMember) noPermissions();
    }
    private void noPermissions() {
        embed.setAsError("No Permissions", ":x: **You Have No Permissions To See This Help Information**");
    }
}