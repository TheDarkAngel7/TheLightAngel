package Angel.BotAbuse;

import Angel.CommonLogic;

interface BotAbuseConfig extends CommonLogic {
    BotAbuseConfiguration botConfig = new ModifyBotAbuseConfiguration(new FileHandler());
}
