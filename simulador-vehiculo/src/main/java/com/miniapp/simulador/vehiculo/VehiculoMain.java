/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.miniapp.simulador.vehiculo;

/**
 *
 * @author SDavidLedesma
 */
public class VehiculoMain {

    public static void main(String[] args) {
        System.out.println("Simulador de Vehículo Iniciado...");

        GpsSimulador gps = new GpsSimulador();

        gps.iniciarEnvio();
        
        // Intentar avisar al servidor que el camión arrancó
        try {
            java.net.URL url = new java.net.URL("http://localhost:8081/api/reportes/hola");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            int status = con.getResponseCode();
            if (status == 200) {
                System.out.println("Conexión con Servidor Central: EXITOSA");
            }
        } catch (Exception e) {
            System.out.println("Error al conectar: " + e.getMessage());
        }
    }
}
