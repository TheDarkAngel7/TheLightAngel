package Angel;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

class RabbitMQSend {
    ConnectionFactory factory = new ConnectionFactory();
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    RabbitMQSend() throws IOException, TimeoutException {
    }

    void startup(String host) throws IOException {
        factory.setHost(host);
        channel.queueDeclare("safe_events", false, false, false, null);
    }


    void sendMessage(String message, String routingKey) throws IOException {
        channel.basicPublish(message, routingKey, false, null, message.getBytes());
    }
}