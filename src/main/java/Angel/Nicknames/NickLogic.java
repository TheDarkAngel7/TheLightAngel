package Angel.Nicknames;

import Angel.CommonLogic;

interface NickLogic extends CommonLogic {
    NickConfiguration nickConfig = new ModifyNickConfiguration(new FileHandler().getConfig());
    NicknameMain nickFeature = new NicknameMain();
    NicknameCore nickCore = new NicknameCore();
}