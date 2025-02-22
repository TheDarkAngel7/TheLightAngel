package Angel.BotAbuse;

import Angel.CommonLogic;

interface BotAbuseLogic extends CommonLogic {
    BotAbuseConfiguration botConfig = new ModifyBotAbuseConfiguration(new FileHandler());
    BotAbuseMain baFeature = new BotAbuseMain();
    BotAbuseCore baCore = new BotAbuseCore();
}
