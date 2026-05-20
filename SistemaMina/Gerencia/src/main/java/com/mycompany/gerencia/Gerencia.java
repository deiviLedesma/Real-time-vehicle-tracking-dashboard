package com.mycompany.gerencia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Gerencia {

    private static final String ALMACENAMIENTO_URL = System.getenv().getOrDefault(
            "ALMACENAMIENTO_URL",
            "http://localhost:8085/api/historial/congestiones");

    public static void main(String[] args) {
        pedirHistorialCongestiones();
    }

    public static void pedirHistorialCongestiones() {
        try {
            HttpClient cliente = HttpClient.newHttpClient();

            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(URI.create(ALMACENAMIENTO_URL))
                    .GET()
                    .build();

            HttpResponse<String> respuesta = cliente.send(
                    peticion,
                    HttpResponse.BodyHandlers.ofString());

            if (respuesta.statusCode() == 200) {
                System.out.println(" [OK] Datos recibidos: " + respuesta.body());
            } else {
                System.out.println(" [X] Error del servidor. Codigo HTTP: " + respuesta.statusCode());
            }

        } catch (Exception e) {
            System.err.println(" [X] No se pudo conectar al servicio de almacenamiento.");
            e.printStackTrace();
        }
    }
}
