/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mina.semaforo;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 
 * @author lagar
 */
public class SemaforoMain {

    private static final String BROKER = "tcp://broker.hivemq.com:1883";
    private static final String CLIENT_ID = "Semaforo_Mina_01";
    private static final String TOPIC_CONTROL = "mina/semaforo/1/control";
    
    private static String estadoActual = "VERDE";

    public static void main(String[] args) {
        System.out.println("=== CONTROLADOR DE SEMÁFORO IoT INICIADO ===");
        
        try {
            MqttClient client = new MqttClient(BROKER, CLIENT_ID);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Conexión perdida. Reintentando...");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String comando = new String(message.getPayload()).toUpperCase();
                    System.out.println("\n[Comando Recibido]: " + comando);
                    
                    if (comando.equals("ROJO") || comando.equals("VERDE")) {
                        estadoActual = comando;
                        dibujarSemaforo();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            System.out.println("Conectando al broker: " + BROKER);
            client.connect(options);
            client.subscribe(TOPIC_CONTROL);
            System.out.println("Suscrito al tópico: " + TOPIC_CONTROL);
            
            dibujarSemaforo();

        } catch (MqttException e) {
            System.err.println("Error en el semáforo: " + e.getMessage());
        }
    }

    private static void dibujarSemaforo() {
        String ROJO = "\u001B[31m";
        String VERDE = "\u001B[32m";
        String RESET = "\u001B[0m";

        System.out.println("\n------- ESTADO -------");
        if (estadoActual.equals("ROJO")) {
            System.out.println(ROJO + "    ( ● ) ROJO" + RESET);
            System.out.println("    (   ) ");
        } else {
            System.out.println("    (   ) ");
            System.out.println(VERDE + "    ( ● ) VERDE" + RESET);
        }
        System.out.println("----------------------");
    }
}
