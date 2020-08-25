package Angel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class Ping {
    String reader = "";

    long getGatewayNetPing() {
        long returnValue = 0;
        try {
            Process p = Runtime.getRuntime().exec("ping gateway.discord.gg");
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((reader = inputStream.readLine()) != null) {
                if (reader.contains("Average =")) {
                    returnValue = Long.parseLong(reader.substring(reader.lastIndexOf("=") + 2).split("m")[0]);
                }
            }
            inputStream.close();
            return returnValue;
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return 0;
        }
    }
}
