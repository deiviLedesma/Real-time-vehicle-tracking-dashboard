package com.mycompany.almacenamientoe;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/escritura")
@Produces(MediaType.APPLICATION_JSON)
public class EscrituraResource {

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "almacenamiento-e");
    }
}
