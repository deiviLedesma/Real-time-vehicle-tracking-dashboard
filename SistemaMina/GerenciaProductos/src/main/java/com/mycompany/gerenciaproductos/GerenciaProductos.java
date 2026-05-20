package com.mycompany.gerenciaproductos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GerenciaProductos {

    private static final String REPORTES_URL = System.getenv().getOrDefault(
            "REPORTES_URL",
            "http://host.docker.internal/api/reportes/productos");

    public static void main(String[] args) {
        consultarReporte("productos");
    }

    private static void consultarReporte(String reporte) {
        try {
            HttpClient cliente = HttpClient.newHttpClient();
            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(URI.create(REPORTES_URL))
                    .GET()
                    .build();

            HttpResponse<String> respuesta = cliente.send(
                    peticion,
                    HttpResponse.BodyHandlers.ofString());

            if (respuesta.statusCode() == 200) {
                System.out.println(" [OK] Reporte de " + reporte + ": " + respuesta.body());
            } else {
                System.out.println(" [X] Error HTTP " + respuesta.statusCode() + " consultando " + reporte);
            }
        } catch (Exception e) {
            System.err.println(" [X] No se pudo consultar el reporte de productos.");
            e.printStackTrace();
        }
    }
}
