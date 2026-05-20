package com.mycompany.semaforo;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Semaforo implements MqttCallback {

    private static final String MQTT_HOST = System.getenv().getOrDefault("MQTT_HOST", "localhost");
    private static final String MQTT_PORT = System.getenv().getOrDefault("MQTT_PORT", "1883");
    private static final String MQTT_USER = System.getenv().getOrDefault("MQTT_USER", "guest");
    private static final String MQTT_PASSWORD = System.getenv().getOrDefault("MQTT_PASSWORD", "guest");

    private final String idSemaforo = System.getenv().getOrDefault("SEMAFORO_ID", "01");
    private final int autoToggleSeconds = parsePositiveInt(
            System.getenv().getOrDefault("AUTO_TOGGLE_SECONDS", "10"), 10);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MqttClient client;
    private volatile String estadoActual = "ROJO";

    public Semaforo() {
        try {
            String brokerUrl = "tcp://" + MQTT_HOST + ":" + MQTT_PORT;
            client = new MqttClient(brokerUrl, "Semaforo-" + idSemaforo);

            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(MQTT_USER);
            options.setPassword(MQTT_PASSWORD.toCharArray());

            String topicEstado = "semaforos/" + idSemaforo + "/estado";
            options.setWill(topicEstado, "DESCONECTADO".getBytes(), 1, true);

            client.connect(options);
            System.out.println("Semaforo " + idSemaforo + " conectado a RabbitMQ.");

            client.subscribe("semaforos/" + idSemaforo + "/comandos", 1);
            enviarEstado("ROJO");
            iniciarAlternanciaAutomatica();

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String comando = new String(message.getPayload());
        System.out.println("Orden recibida del servidor: " + comando);

        if (comando.equals("CAMBIAR_A_VERDE")) {
            System.out.println("Cambiando luz fisica a VERDE...");
            enviarEstado("VERDE");
        } else if (comando.equals("CAMBIAR_A_ROJO")) {
            System.out.println("Cambiando luz fisica a ROJO...");
            enviarEstado("ROJO");
        }
    }

    public void enviarEstado(String estado) {
        try {
            MqttMessage mensaje = new MqttMessage(estado.getBytes());
            mensaje.setQos(1);
            mensaje.setRetained(true);

            String topicEstado = "semaforos/" + idSemaforo + "/estado";
            client.publish(topicEstado, mensaje);
            estadoActual = estado;
            System.out.println("Estado actualizado enviado a RabbitMQ: " + estado);

        } catch (MqttException e) {
            System.out.println("Error al intentar enviar el estado al servidor.");
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Senal perdida. El hardware intentara reconectar solo...");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public static void main(String[] args) {
        new Semaforo();
    }

    private void iniciarAlternanciaAutomatica() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String siguienteEstado = "ROJO".equals(estadoActual) ? "VERDE" : "ROJO";
                System.out.println("Alternancia automatica: cambiando a " + siguienteEstado + "...");
                enviarEstado(siguienteEstado);
            } catch (Exception e) {
                System.out.println("Error en la alternancia automatica del semaforo.");
            }
        }, autoToggleSeconds, autoToggleSeconds, TimeUnit.SECONDS);
        System.out.println("Alternancia automatica activada cada " + autoToggleSeconds + " segundos.");
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
