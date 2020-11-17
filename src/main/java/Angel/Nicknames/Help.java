package Angel.Nicknames;

import Angel.EmbedHandler;
import Angel.MainConfiguration;

class Help {
    private EmbedHandler embed;
    private NicknameMain nickMain;
    private MainConfiguration mainConfig;

    Help(EmbedHandler embed, NicknameMain nickMain, MainConfiguration mainConfig) {
        this.embed = embed;
        this.nickMain = nickMain;
        this.mainConfig = mainConfig;
    }
    private String restrictedRoleMentions() {
        String result = "**Only Usable by players in:**\n";
        int index = 0;

        while (index < nickMain.nickConfig.restrictedRoles.size()) {
            result = result.concat("\n**-" + nickMain.nickConfig.restrictedRoles.get(index++).getAsMention());
        }
        return result;
    }

    void requestCommand() {
        embed.setAsHelp(mainConfig.commandPrefix + "nickname request", restrictedRoleMentions() +
                "\n\n`" + mainConfig.commandPrefix + "nickname request <newNickname>`" +
                "\n`" + mainConfig.commandPrefix + "nn req <newNickname>`" +
                "\n\nThis command is for submitting a nickname " +
                "change request to the staff, pretty straight forward. " +
                "When submitted the bot will respond with Request ID, Discord ID, old nickname, requested new nickname." +
                "\n\nIf you place \"reset\" in the `<newNickname>` argument, then the request that you submit is requesting " +
                "that your nickname gets erased so your discord username gets displayed.");
    }
    void withdrawCommand() {
        embed.setAsHelp(mainConfig.commandPrefix + "nickname withdraw", restrictedRoleMentions() +
                "\n\n`" + mainConfig.commandPrefix + "nickname withdraw`" +
                "\n`" + mainConfig.commandPrefix + "nn wd`" +
                "\nThis command is also only usable by players who have a pending nickname request, " +
                "this command withdraws their request, but does not cancel the cooldown");
    }
    void acceptCommand(boolean isTeamMember) {
        if (isTeamMember) {
            embed.setAsHelp(mainConfig.commandPrefix + "nickname accept",
                    "**Only usable by Staff Members (Jr. Staff+)**" +
                            "\n\n`" + mainConfig.commandPrefix + "nickname accept <Mention or Request ID>`" +
                            "\n`" + mainConfig.commandPrefix + "nn acc <Mention or Request ID>`" +
                            "\n`" + mainConfig.commandPrefix + "nn a <Mention or Request ID>`" +
                            "\n" +
                            "This command is for accepting a nickname change request, upon accepting the bot will DM " +
                            "the member who submitted the request, change the member's nickname to the requested nickname " +
                            "in the request, and print in the discussion channel that the request was accepted.");
        }
        else {
            embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions to see this help**");
        }
    }
    void denyCommand(boolean isTeamMember) {
        if (isTeamMember) {
            embed.setAsHelp(mainConfig.commandPrefix + "nickname deny",
                    "**Only usable by Staff Members (Jr. Staff+)**" +
                            "\n\n`" + mainConfig.commandPrefix + "nickname deny <Mention or Request ID>`" +
                            "\n`" + mainConfig.commandPrefix + "nn d <Mention or Request ID>`" +
                            "\n" +
                            "Does exactly the same thing as accepting except the nickname is not changed by the bot. " +
                            "DM the original submitter that the request was denied and print in the discussion channel that " +
                            "the request was denied.");
        }
        else {
            embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions to see this help**");
        }
    }
    void historyCommand(boolean isTeamMember) {
        if (isTeamMember) {
            embed.setAsHelp(mainConfig.commandPrefix + "nickname history",
                    "**Only usable by Staff Members (Jr. Staff+)**" +
                            "\n\n`" + mainConfig.commandPrefix + "nickname history <Mention or Discord ID>`" +
                            "\n`" + mainConfig.commandPrefix + "nn h <Mention or Request ID>`" +
                            "\n" +
                            "This prints all of the captured effective name changes of the player ID or mention given. " +
                            "When I say \"effective name\", I mean the name that is displayed within our discord server.");
        }
        else {
            embed.setAsError("No Permissions", ":x: **You Do Not Have Permissions to see this help**");
        }
    }
    void forceChangeCommand() {
        embed.setAsHelp(mainConfig.commandPrefix + "nickname forcechange",
        "***Use this command to change someone else's nickname***" +
                "\n\n`" + mainConfig.commandPrefix + "nickname forcechange <Mention or Discord ID> <New Nickname>`" +
                "\n`" + mainConfig.commandPrefix + "nn fch <Mention or Discord ID> <New Nickname>`" +
                "\n\n**Only usable by Staff Members (Jr. Staff+)**" +
                "\n\n" +
                "Forcefully changes someone's nickname. You cannot use Discord's GUI to change another player's nickname " +
                "due to the bot listening when that happens (Discord API limitations). If you try to change someone else's " +
                "nickname with Discord's GUI... the bot will think the player who's nickname you tried to change tried to " +
                "change their own nickname. Then what'll happen is the player will receive the same message they would receive " +
                "if they tried to change their nickname. That's due to Discord's API limitations. \n" +
                "\n" +
                "This command makes the bot ignore the next nickname update event, so that the player you tried to change " +
                "the nickname of does not get notified as if they themselves tried to change their own nickname.");
    }
    void listCommand() {
        embed.setAsHelp(mainConfig.commandPrefix + "nickname list",
                "" + mainConfig.commandPrefix + "nickname list" +
                        "\n" + mainConfig.commandPrefix + "nn list" +
                        "\nLists all the pending nickname requests");
    }
}
