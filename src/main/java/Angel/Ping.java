package Angel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class Ping {
    private final Logger log = LogManager.getLogger(Ping.class);
    private String reader = "";

    long getGatewayNetPing() {
        long returnValue = 0;
        try {
            Process p = Runtime.getRuntime().exec("ping gateway.discord.gg");
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));

            int index = 0;
            while ((reader = inputStream.readLine()) != null) {
                if (reader.contains("Average =")) {
                    returnValue = Long.parseLong(reader.substring(reader.lastIndexOf("=") + 2).split("m")[0]);
                    log.info("Network Ping Time to Discord's Gateway: " + returnValue + "ms");
                }
                else {
                    try {
                        log.info("Packet " + ++index + ": " + reader.split("time=")[1].substring(0, 4));
                    }
                    catch (ArrayIndexOutOfBoundsException ex) {
                        index--;
                    }
                }
            }
            inputStream.close();
            return returnValue;
        }
        catch (IOException ex) {
            log.error("Caught IOException - Returning 0ms\n" + ex.getMessage());
            return 0;
        }
    }
}
