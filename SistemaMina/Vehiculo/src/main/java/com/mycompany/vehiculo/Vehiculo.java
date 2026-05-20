package com.mycompany.vehiculo;

import ch.hsr.geohash.GeoHash;
import java.nio.charset.StandardCharsets;
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
        double latActual = Double.parseDouble(System.getenv().getOrDefault("LAT_INICIAL", "27.481000"));
        double lonActual = Double.parseDouble(System.getenv().getOrDefault("LON_INICIAL", "-109.931000"));
        double pasoLatitud = Double.parseDouble(System.getenv().getOrDefault("PASO_LATITUD", "0.00002"));
        int geohashPrecision = Integer.parseInt(System.getenv().getOrDefault("GEOHASH_PRECISION", "7"));
        int totalShards = Integer.parseInt(System.getenv().getOrDefault("CONGESTIONES_SHARDS", "2"));
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

            while (true) {
                // Avanzar un poquito hacia el NORTE
                latActual += pasoLatitud;
                long timestamp = System.currentTimeMillis();

                String geohash = GeoHash.geoHashStringWithCharacterPrecision(
                        latActual,
                        lonActual,
                        geohashPrecision
                );
                int shard = Math.floorMod(geohash.hashCode(), totalShards) + 1;

                String payload = String.format(Locale.US,
                        "{\"latitud\": %.6f, \"longitud\": %.6f, \"id\": \"%s\", \"timestamp\": %d, \"geohash\": \"%s\", \"shard\": %d}",
                        latActual,
                        lonActual,
                        idVehiculo,
                        timestamp,
                        geohash,
                        shard);

                if (publisher.isConnected()) {
                    MqttMessage msgPosicion = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                    msgPosicion.setQos(0);

                    String topicPosicion = "mineria/vehiculos/posicion/shard-" + shard + "/" + geohash;
                    publisher.publish(topicPosicion, msgPosicion);

                    MqttMessage msgPersistencia = new MqttMessage("hierro cargamento".getBytes(StandardCharsets.UTF_8));
                    msgPersistencia.setQos(1);

                    String topicPersistencia = "mina/vehiculos/descarga/" + idVehiculo;
                    publisher.publish(topicPersistencia, msgPersistencia);

                    System.out.println("Enviado -> Topic: " + topicPosicion + " | Payload: " + payload);
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
