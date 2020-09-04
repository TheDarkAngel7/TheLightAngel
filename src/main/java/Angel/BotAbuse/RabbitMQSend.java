package Angel.BotAbuse;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

class RabbitMQSend {
    private ConnectionFactory factory = new ConnectionFactory();
    private Connection connection = factory.newConnection();
    private Channel channel = connection.createChannel();

    RabbitMQSend() throws IOException, TimeoutException {
    }

    void startup(String host) throws IOException {
        try {
            factory.setUri(host);
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        channel.queueDeclare("safe_events", false, false, false, null);
    }


    void sendMessage(String message, String routingKey) throws IOException {
        channel.basicPublish(message, routingKey, false, null, message.getBytes());
    }
}