/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.miniapp.simulador.vehiculo;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 *
 * @author SDavidLedesma
 */
public class GpsSimulador {
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC = "mina/vehiculo/1/posicion";

    public void iniciarEnvio() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, MqttClient.generateClientId());
            client.connect();
            
            double lat = -27.36; 
            double lon = -70.33;

            while (true) {
                lat += 0.0001;
                String payload = "Lat: " + lat + ", Lon: " + lon;
                
                MqttMessage message = new MqttMessage(payload.getBytes());
                client.publish(TOPIC, message);
                
                System.out.println("Enviando GPS: " + payload);
                Thread.sleep(3000); 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}