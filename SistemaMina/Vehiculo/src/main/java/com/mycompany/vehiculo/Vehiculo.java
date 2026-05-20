package com.mycompany.vehiculo;

import java.util.Locale;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Vehiculo {

    public static void main(String[] args) {
        String idVehiculo = System.getenv().getOrDefault("VEHICULO_ID", "camion-01");
        String mqttHost = System.getenv().getOrDefault("MQTT_HOST", "localhost");
        String mqttPort = System.getenv().getOrDefault("MQTT_PORT", "1883");
        String mqttUser = System.getenv().getOrDefault("MQTT_USER", "guest");
        String mqttPassword = System.getenv().getOrDefault("MQTT_PASSWORD", "guest");
        String publisherId = UUID.randomUUID().toString();

        try {
            MqttClient publisher = new MqttClient("tcp://" + mqttHost + ":" + mqttPort, publisherId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setUserName(mqttUser);
            options.setPassword(mqttPassword.toCharArray());

            publisher.connect(options);
            System.out.println("Vehiculo " + idVehiculo + " conectado a RabbitMQ.");

            // Coordenadas iniciales
            double latActual = 27.4841; 
            double lonActual = -109.9300;

            while (true) {
                // Avanzar un poquito hacia el NORTE
                latActual += 0.00002; 
                long timestamp = System.currentTimeMillis();

                String payload = String.format(Locale.US,
                        "{\"latitud\": %.6f, \"longitud\": %.6f, \"id\": \"%s\", \"timestamp\": %d}",
                        latActual,
                        lonActual,
                        idVehiculo,
                        timestamp);

                if (publisher.isConnected()) {
                    MqttMessage msgPosicion = new MqttMessage(payload.getBytes());
                    msgPosicion.setQos(0);
                    // topic "mineria/vehiculos/posicion" en cola es "mineria.vehiculos.posicion"
                    publisher.publish("mineria/vehiculos/posicion", msgPosicion);

                    MqttMessage msgPersistencia = new MqttMessage("hierro cargamento".getBytes());
                    msgPersistencia.setQos(1);

                    String topicPersistencia = "mina/vehiculos/descarga/" + idVehiculo;
                    publisher.publish(topicPersistencia, msgPersistencia);

                    System.out.println("Enviado -> Posicion: " + payload);
                } else {
                    System.out.println("Sin senal... posicion perdida.");
                }

                Thread.sleep(3000);
            }

        } catch (Exception e) {
            System.out.println("Error critico en el sistema del vehiculo: " + e.getMessage());
        }
    }
}
