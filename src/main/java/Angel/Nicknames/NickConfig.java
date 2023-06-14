package Angel.Nicknames;

import Angel.MainConfig;

interface NickConfig extends MainConfig {
    NickConfiguration nickConfig = new ModifyNickConfiguration(new FileHandler().getConfig());
}