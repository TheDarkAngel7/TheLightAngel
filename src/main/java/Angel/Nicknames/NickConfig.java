package Angel.Nicknames;

import Angel.CommonLogic;

interface NickConfig extends CommonLogic {
    NickConfiguration nickConfig = new ModifyNickConfiguration(new FileHandler().getConfig());
}