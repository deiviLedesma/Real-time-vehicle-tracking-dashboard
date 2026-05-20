package com.mycompany.congestiones;

import ch.hsr.geohash.GeoHash;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.mina.notificaciones.grpc.MutinyNotificacionesServiceGrpc;
import org.mina.notificaciones.grpc.NotificacionesProto;
import org.mina.topologia.grpc.TopologiaProto;
import org.mina.topologia.grpc.TopologiaServiceGrpc;

@ApplicationScoped
public class Congestiones {

    @GrpcClient("cliente-notificaciones")
    MutinyNotificacionesServiceGrpc.MutinyNotificacionesServiceStub notificacionesAsyncClient;

    @GrpcClient("cliente-topologia")
    TopologiaServiceGrpc.TopologiaServiceBlockingStub topologiaClient;

    @Inject
    ObjectMapper mapper;

    @Channel("congestiones-out")
    Emitter<String> emisor;

    // --- ALMACENAMIENTO EN MEMORIA (ESTADO) ---
    private static final double UMBRAL_VELOCIDAD_LENTA_KMH = 10.0;
    private static final int UMBRAL_CAMIONES_CONGESTION = 3; // Ajustado para el entorno de la mina
    private static final int PRECISION_GEOHASH = 7; // ~150 metros

    private final Map<String, EstadoVehiculo> vehiculos = new ConcurrentHashMap<>();
    private final Map<String, Semaforo> semaforos = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> vehiculosPorSector = new ConcurrentHashMap<>();
    
    private static final long COOLDOWN_ALERTAS_MS = 60000; 
    private final Map<String, Long> ultimaAlertaPorSemaforo = new ConcurrentHashMap<>();

    void onStart(@Observes StartupEvent ev) {
        System.out.println(" [*] Microservicio Congestiones inicializado.");
        analizarTraficoMina();
    }

    public void analizarTraficoMina() {
        System.out.println(" [*] Solicitando topologia a SemaforosTopologia via gRPC...");
        try {
            TopologiaProto.EmptyRequest request = TopologiaProto.EmptyRequest.newBuilder().build();
            TopologiaProto.SemaforoListResponse response = topologiaClient.getSemaforos(request);

            System.out.println(" [OK] Topologia recibida con " + response.getSemaforosCount() + " semaforos.");

            for (TopologiaProto.Semaforo semaforoDto : response.getSemaforosList()) {
                // Generar Geohash
                String geohash = GeoHash.geoHashStringWithCharacterPrecision(
                        semaforoDto.getLatitud(), semaforoDto.getLongitud(), PRECISION_GEOHASH);

                Semaforo semaforoObj = new Semaforo(
                        semaforoDto.getId(),
                        new Posicion(semaforoDto.getLatitud(), semaforoDto.getLongitud()),
                        semaforoDto.getSentido(),
                        geohash
                );
                
                semaforos.put(semaforoObj.id, semaforoObj);

                System.out.printf("     -> Semaforo [%s]: Sector Geohash [%s]%n", semaforoObj.id, geohash);
            }
        } catch (Exception e) {
            System.err.println(" [X] Error al contactar al servidor gRPC: " + e.getMessage());
        }
    }

    @Incoming("vehiculos-in")
    public CompletionStage<Void> recibirGpsVehiculo(Message<byte[]> mensaje) {
        try {
            String mensajeGps = new String(mensaje.getPayload(), StandardCharsets.UTF_8);
            if (!mensajeGps.trim().startsWith("{")) {
                return mensaje.ack();
            }

            System.out.println("GpsVehiculo recibido");
            
            JsonNode nodo = mapper.readTree(mensajeGps);
            String idVehiculo = nodo.path("id").asText("");
            double lat = nodo.path("latitud").asDouble();
            double lon = nodo.path("longitud").asDouble();
            long timestamp = nodo.path("timestamp").asLong(System.currentTimeMillis());

            Posicion nuevaPosicion = new Posicion(lat, lon);
            String nuevoGeohash = GeoHash.geoHashStringWithCharacterPrecision(lat, lon, PRECISION_GEOHASH);

            EstadoVehiculo estadoAnterior = vehiculos.get(idVehiculo);
            EstadoVehiculo estadoActual = new EstadoVehiculo(idVehiculo, nuevaPosicion, timestamp);
            estadoActual.geohashActual = nuevoGeohash;

            if (estadoAnterior != null) {
                estadoActual.direccionActual = calcularDireccion(estadoAnterior.posicion, nuevaPosicion);
                estadoActual.velocidadKmh = calcularVelocidad(estadoAnterior, estadoActual);

                if (!nuevoGeohash.equals(estadoAnterior.geohashActual)) {
                    removerDeSector(estadoAnterior.geohashActual, idVehiculo);
                    agregarASector(nuevoGeohash, idVehiculo);
                }
            } else {
                agregarASector(nuevoGeohash, idVehiculo);
            }

            vehiculos.put(idVehiculo, estadoActual);

            // EVALUACIÓN DE CONGESTIÓN
            if (estadoActual.velocidadKmh < UMBRAL_VELOCIDAD_LENTA_KMH && estadoActual.velocidadKmh > 0) {
                evaluarCongestionSiAplica(nuevoGeohash, estadoActual.direccionActual);
            }

            return mensaje.ack();
        } catch (Exception e) {
            System.err.println(" [X] Error procesando GPS del vehiculo.");
            e.printStackTrace();
            return mensaje.nack(e);
        }
    }

    private void evaluarCongestionSiAplica(String geohash, String direccionCamion) {
        // Busca si en la zona actual del camión hay un semáforo en rojo en su misma dirección
        semaforos.values().stream()
                .filter(s -> s.geohash.equals(geohash) && s.enRojo && s.sentido.equals(direccionCamion))
                .findFirst()
                .ifPresent(semaforo -> {
                    Set<String> vehiculosEnSector = vehiculosPorSector.getOrDefault(semaforo.geohash, Set.of());
                    
                    long camionesLentos = vehiculosEnSector.stream()
                            .map(vehiculos::get)
                            .filter(v -> v != null)
                            .filter(v -> v.direccionActual.equals(semaforo.sentido))
                            .filter(v -> v.velocidadKmh < UMBRAL_VELOCIDAD_LENTA_KMH)
                            .count();

                    if (camionesLentos >= UMBRAL_CAMIONES_CONGESTION) {
                        long ahora = System.currentTimeMillis();
                        long ultimaAlerta = ultimaAlertaPorSemaforo.getOrDefault(semaforo.id, 0L);

                        // cooldown
                        if (ahora - ultimaAlerta >= COOLDOWN_ALERTAS_MS) {
                            ultimaAlertaPorSemaforo.put(semaforo.id, ahora);

                            enviarAlertaAsincrona("Congestion detectada", "Alta", semaforo.id);
                            reportarCongestion("Congestion en semaforo " + semaforo.id + ". Camiones: " + camionesLentos);
                        } 
                        else {
                            System.out.println(" [Debounce] Congestion activa en " + semaforo.id + 
                                               " detectada, pero ignorando por cooldown.");
                        }
                    }
                });
    }

    // --- MÉTODOS DE gRPC Y RABBITMQ ------------
    private void enviarAlertaAsincrona(String mensaje, String severidad, String idSemaforo) {
        NotificacionesProto.AlertaRequest request = NotificacionesProto.AlertaRequest.newBuilder()
                .setMensaje(mensaje)
                .setNivelSeveridad(severidad)
                .setIdSemaforo(idSemaforo)
                .build();

        notificacionesAsyncClient.enviarAlerta(request)
                .subscribe().with(
                        respuesta -> System.out.println(" [OK Async] Alerta enviada para " + idSemaforo),
                        error -> System.err.println(" [X Async] Fallo: " + error.getMessage())
                );
    }

    public void reportarCongestion(String datosCongestion) {
        emisor.send(datosCongestion);
        System.out.println("[Enviado] Alerta al exchange: " + datosCongestion);
    }

    // --- INTERNO ---
    private void agregarASector(String geohash, String vehiculoId) {
        vehiculosPorSector.computeIfAbsent(geohash, k -> ConcurrentHashMap.newKeySet()).add(vehiculoId);
    }

    private void removerDeSector(String geohash, String vehiculoId) {
        Set<String> sector = vehiculosPorSector.get(geohash);
        if (sector != null) {
            sector.remove(vehiculoId);
            if (sector.isEmpty()) {
                vehiculosPorSector.remove(geohash);
            }
        }
    }

    private String calcularDireccion(Posicion p1, Posicion p2) {
        double deltaLat = p2.latitud - p1.latitud;
        double deltaLon = p2.longitud - p1.longitud;
        if (Math.abs(deltaLat) > Math.abs(deltaLon)) {
            return deltaLat > 0 ? "NORTE" : "SUR";
        } else {
            return deltaLon > 0 ? "ESTE" : "OESTE";
        }
    }

    private double calcularVelocidad(EstadoVehiculo anterior, EstadoVehiculo actual) {
        double radioTierraKm = 6371.0;
        double dLat = Math.toRadians(actual.posicion.latitud - anterior.posicion.latitud);
        double dLon = Math.toRadians(actual.posicion.longitud - anterior.posicion.longitud);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(anterior.posicion.latitud)) * Math.cos(Math.toRadians(actual.posicion.latitud)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanciaMetros = (radioTierraKm * c) * 1000;

        double tiempoSegundos = (actual.timestamp - anterior.timestamp) / 1000.0;
        if (tiempoSegundos <= 0) return 0.0;
        return (distanciaMetros / tiempoSegundos) * 3.6; // km/h
    }

    // ---------------------------------------------
    public static class Posicion {
        public double latitud, longitud;
        public Posicion(double latitud, double longitud) { this.latitud = latitud; this.longitud = longitud; }
    }

    public static class EstadoVehiculo {
        public String id;
        public Posicion posicion;
        public String direccionActual = "DESCONOCIDO";
        public double velocidadKmh = 0.0;
        public String geohashActual = "";
        public long timestamp;
        public EstadoVehiculo(String id, Posicion posicion, long timestamp) { this.id = id; this.posicion = posicion; this.timestamp = timestamp; }
    }

    public static class Semaforo {
        public String id;
        public Posicion posicion;
        public String sentido;
        public boolean enRojo = true; // Empiezan en rojo por defecto
        public String geohash;
        public Semaforo(String id, Posicion posicion, String sentido, String geohash) {
            this.id = id; this.posicion = posicion; this.sentido = sentido; this.geohash = geohash;
        }
    }
}