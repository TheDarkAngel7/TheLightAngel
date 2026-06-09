package Angel.PlayerList.Exceptions;

public class CooldownConfigDoesNotExist extends RuntimeException {
    public CooldownConfigDoesNotExist(String sessionName) {
        super("Could Not Find SessionCooldownConfiguration with Session Name: " + sessionName);
    }
}
