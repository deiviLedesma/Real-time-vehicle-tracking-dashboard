package com.mycompany.gerenciaproductos;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GerenciaProductos {
    private static final int REPETICIONES = Integer.parseInt(System.getenv().getOrDefault(
            "REPETICIONES_REPORTE",
            "2"));

    private static final String REPORTES_URL = System.getenv().getOrDefault(
            "REPORTES_URL",
            "http://host.docker.internal/api/reportes/productos");
    private static final String TOKEN_URL = System.getenv().getOrDefault(
            "TOKEN_URL",
            "http://host.docker.internal:8180/realms/mvts/protocol/openid-connect/token");
    private static final String CLIENT_ID = System.getenv().getOrDefault(
            "KEYCLOAK_CLIENT_ID",
            "mvts-gerencia-cli");
    private static final String USERNAME = System.getenv().getOrDefault(
            "KEYCLOAK_USERNAME",
            "gerencia-productos");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "KEYCLOAK_PASSWORD",
            "gerencia123");

    public static void main(String[] args) {
        consultarReporte("productos");
    }

    private static void consultarReporte(String reporte) {
        try {
            HttpClient cliente = HttpClient.newHttpClient();
            System.out.println(" [INFO] Obteniendo token una sola vez para " + reporte + "...");
            String token = obtenerToken(cliente);
            System.out.println(" [INFO] Token obtenido. Prefijo: " + resumirToken(token));

            for (int intento = 1; intento <= REPETICIONES; intento++) {
                HttpRequest peticion = HttpRequest.newBuilder()
                        .uri(URI.create(REPORTES_URL))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> respuesta = cliente.send(
                        peticion,
                        HttpResponse.BodyHandlers.ofString());

                if (respuesta.statusCode() == 200) {
                    System.out.println(" [OK] Consulta " + intento + "/" + REPETICIONES
                            + " con el mismo token: " + respuesta.body());
                } else {
                    System.out.println(" [X] Error HTTP " + respuesta.statusCode()
                            + " en consulta " + intento + " de " + reporte);
                }
            }
        } catch (Exception e) {
            System.err.println(" [X] No se pudo consultar el reporte de productos.");
            e.printStackTrace();
        }
    }

    private static String obtenerToken(HttpClient cliente) throws Exception {
        String form = "grant_type=password"
                + "&client_id=" + encode(CLIENT_ID)
                + "&username=" + encode(USERNAME)
                + "&password=" + encode(PASSWORD);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> tokenResponse = cliente.send(
                tokenRequest,
                HttpResponse.BodyHandlers.ofString());

        if (tokenResponse.statusCode() != 200) {
            throw new IllegalStateException("No se pudo obtener token. HTTP "
                    + tokenResponse.statusCode() + ": " + tokenResponse.body());
        }

        Matcher matcher = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(tokenResponse.body());
        if (!matcher.find()) {
            throw new IllegalStateException("La respuesta del token no trae access_token.");
        }

        return matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String resumirToken(String token) {
        int limite = Math.min(token.length(), 18);
        return token.substring(0, limite) + "...";
    }
}
