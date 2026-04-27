/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example.demo;

import com.example.demo.repository.PosicionRepository;
import com.example.demo.model.PosicionVehiculo;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author SDavidLedesma
 */
@Component
public class MqttListener {

    @Autowired
    private PosicionRepository repository;

    private final String BROKER = "tcp://broker.hivemq.com:1883";

    @PostConstruct
    public void conectar() {
        try {
            MqttClient client = new MqttClient(BROKER, MqttClient.generateClientId());
            client.setCallback(new MqttCallback() {
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = message.toString();
                    System.out.println("Procesando: " + payload);

                    try {
                        String[] partes = payload.split(",");
                        double lat = Double.parseDouble(partes[0].replace("Lat: ", "").trim());
                        double lon = Double.parseDouble(partes[1].replace("Lon: ", "").trim());
                        String vehiculoId = topic.split("/")[2]; 

                        PosicionVehiculo nuevaPos = new PosicionVehiculo(vehiculoId, lat, lon);
                        repository.save(nuevaPos);
                        
                        System.out.println("¡ÉXITO! Guardado en DB: Vehículo " + vehiculoId + " en (" + lat + ", " + lon + ")");
                    } catch (Exception e) {
                        System.err.println("Error al procesar los datos: " + e.getMessage());
                    }
                }

                @Override public void deliveryComplete(IMqttDeliveryToken token) {}
                @Override public void connectionLost(Throwable cause) {}
            });

            client.connect();
            client.subscribe("mina/vehiculo/+/posicion");
        } catch (Exception e) { e.printStackTrace(); }
    }
}