package com.mycompany.estacioncentral;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.MessageProperties;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EstacionCentral {

    private static final String ROUTING_KEY_VEHICULOS = "mineria.vehiculos.#";
    private static final String ROUTING_KEY_SEMAFOROS_ESTADO = "semaforos.#.estado";
    private static final String ROUTING_KEY_ALERTAS = "central.notificaciones.alertas";
    private static final String EXCHANGE_MQTT = "amq.topic";
    private static final String EXCHANGE_MINA = "mina.exchange.principal";

    private static final String QUEUE_VEHICULOS = "cola.transitoria.posiciones";
    private static final String QUEUE_SEMAFOROS = "cola.persistente.semaforos";
    private static final String QUEUE_ALERTAS = "cola.persistente.central.alertas";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
        factory.setUsername(System.getenv().getOrDefault("RABBITMQ_USER", "guest"));
        factory.setPassword(System.getenv().getOrDefault("RABBITMQ_PASS", "guest"));
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_MINA, BuiltinExchangeType.TOPIC, true);

        channel.queueDeclare(QUEUE_ALERTAS, true, false, false, null);
        channel.queueBind(QUEUE_ALERTAS, EXCHANGE_MINA, ROUTING_KEY_ALERTAS);
        DeliverCallback alertasCallback = (consumerTag, delivery) -> {
            try {
                String alertaJson = new String(delivery.getBody(), "UTF-8");
                System.out.println(" [!!! ALERTA CRITICA EN CENTRAL !!!] " + alertaJson);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                System.err.println(" Error procesando la alerta.");
                channel.basicReject(delivery.getEnvelope().getDeliveryTag(), true);
            }
        };
        System.out.println(" [*] Escuchando alertas en la estacion central...");
        channel.basicConsume(QUEUE_ALERTAS, false, alertasCallback, consumerTag -> {
        });

        channel.queueDeclare(QUEUE_SEMAFOROS, true, false, false, null);
        channel.queueBind(QUEUE_SEMAFOROS, EXCHANGE_MQTT, ROUTING_KEY_SEMAFOROS_ESTADO);
        DeliverCallback estadosCallback = (consumerTag, delivery) -> {
            String estado = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [SEMAFORO] Estado actualizado recibido: " + estado);
        };
        channel.basicConsume(QUEUE_SEMAFOROS, true, estadosCallback, consumerTag -> {
        });

        channel.queueDeclare(QUEUE_VEHICULOS, true, false, false, null);
        channel.queueBind(QUEUE_VEHICULOS, EXCHANGE_MQTT, ROUTING_KEY_VEHICULOS);
        DeliverCallback vehiculosCallback = (consumerTag, delivery) -> {
            String mensaje = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [GPS] Vehiculo: " + mensaje);
        };
        channel.basicConsume(QUEUE_VEHICULOS, true, vehiculosCallback, consumerTag -> {
        });

        System.out.println(" [*] Solicitando topologia de semaforos por RPC...");
        String topologiaJson = pedirTopologiaRpc(channel);
        System.out.println(" [OK] Topologia recibida: " + topologiaJson);
        System.out.println(" [*] Estacion Central operativa.");

        Thread.sleep(5000);
        cambiarSemaforo(channel, "01", "CAMBIAR_A_VERDE");
    }

    public static String pedirTopologiaRpc(Channel channel) throws Exception {
        String replyQueueName = channel.queueDeclare().getQueue();
        String corrId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        channel.basicPublish("", "rpc.semaforos.topologia", props, "GET_ALL".getBytes("UTF-8"));

        CompletableFuture<String> response = new CompletableFuture<>();

        String cTag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                response.complete(new String(delivery.getBody(), "UTF-8"));
            }
        }, consumerTag -> {
        });

        String result = response.get();
        channel.basicCancel(cTag);
        return result;
    }

    public static void cambiarSemaforo(Channel channel, String idSemaforo, String orden) {
        try {
            String routingKey = "semaforos." + idSemaforo + ".comandos";
            channel.basicPublish(EXCHANGE_MQTT,
                    routingKey,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    orden.getBytes("UTF-8"));

            System.out.println(" [->] Orden enviada al Semaforo " + idSemaforo + ": " + orden);
        } catch (IOException e) {
            System.err.println("Error al enviar orden al semaforo.");
        }
    }
}
