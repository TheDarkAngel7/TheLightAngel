package Angel.PlayerList;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionClientListener implements Runnable {

    @Override
    public void run() {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(5001)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                pool.execute(new SessionClientHandler(clientSocket));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
