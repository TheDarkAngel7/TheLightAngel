package Angel.BotAbuse;

import Angel.MainConfig;

interface BotAbuseConfig extends MainConfig {
    BotAbuseConfiguration botConfig = new ModifyBotAbuseConfiguration(new FileHandler());
}
