package Angel.CheckIn;

import Angel.CommonLogic;

public interface CheckInLogic extends CommonLogic {
    CheckInConfiguration ciConfig = new ModifyCheckInConfiguration(new FileHandler().getConfig());
    CheckInMain ciFeature = new CheckInMain();
    CheckInCore ciCore = new CheckInCore();
}
