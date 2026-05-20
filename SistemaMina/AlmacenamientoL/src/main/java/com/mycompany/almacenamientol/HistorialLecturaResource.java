package com.mycompany.almacenamientol;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class HistorialLecturaResource {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    @GET
    @Path("/historial/congestiones")
    public List<String> obtenerCongestiones() {
        return leerPayloads("historial_congestiones");
    }

    @GET
    @Path("/historial/productos")
    public List<String> obtenerProductos() {
        return leerPayloads("historial_productos");
    }

    @GET
    @Path("/reportes/congestiones")
    public List<String> obtenerReporteCongestiones() {
        return leerPayloads("historial_congestiones");
    }

    @GET
    @Path("/reportes/productos")
    public List<String> obtenerReporteProductos() {
        return leerPayloads("historial_productos");
    }

    private List<String> leerPayloads(String collectionName) {
        MongoCollection<Document> collection = collection(collectionName);
        List<String> payloads = new ArrayList<>();

        for (Document document : collection.find().sort(Sorts.ascending("_id"))) {
            payloads.add(document.getString("payload"));
        }

        return payloads;
    }

    private MongoCollection<Document> collection(String collectionName) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return database.getCollection(collectionName);
    }
}
