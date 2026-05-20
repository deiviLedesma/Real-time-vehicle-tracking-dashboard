package com.mycompany.semaforostopologia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class GestorTopologia {

    @Inject
    ObjectMapper mapper;

    @Inject
    SemaforoService service;

    void onStart(@Observes StartupEvent ev) {
        iniciarServidorRpcRabbitMQ();
    }

    private void iniciarServidorRpcRabbitMQ() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"));
            factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
            factory.setUsername(System.getenv().getOrDefault("RABBITMQ_USER", "guest"));
            factory.setPassword(System.getenv().getOrDefault("RABBITMQ_PASS", "guest"));

            Connection connection = factory.newConnection();
            com.rabbitmq.client.Channel channel = connection.createChannel();

            String rpcQueueName = "rpc.semaforos.topologia";
            channel.queueDeclare(rpcQueueName, true, false, false, null);
            channel.queuePurge(rpcQueueName);

            System.out.println(" [RPC] Servidor RabbitMQ RPC esperando peticiones...");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                        .correlationId(delivery.getProperties().getCorrelationId())
                        .build();

                String respuestaJson = mapper.writeValueAsString(service.listarSemaforos());

                channel.basicPublish("",
                        delivery.getProperties().getReplyTo(),
                        replyProps,
                        respuestaJson.getBytes("UTF-8"));

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };

            channel.basicConsume(rpcQueueName, false, deliverCallback, consumerTag -> {
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
