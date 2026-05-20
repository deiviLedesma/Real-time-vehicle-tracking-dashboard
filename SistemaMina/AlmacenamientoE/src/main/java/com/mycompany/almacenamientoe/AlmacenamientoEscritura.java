package com.mycompany.almacenamientoe;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class AlmacenamientoEscritura {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    @Incoming("congestiones-in")
    public CompletionStage<Void> procesarCongestion(Message<String> mensaje) {
        String payloadCrudo = mensaje.getPayload();

        collection("historial_congestiones").insertOne(new Document()
                .append("payload", payloadCrudo)
                .append("tipo", "congestion")
                .append("createdAt", Instant.now().toString()));

        System.out.println("[Congestion - Mongo Write]: " + payloadCrudo);
        return mensaje.ack();
    }

    @Incoming("productos-in")
    public CompletionStage<Void> procesarProducto(Message<byte[]> mensaje) {
        String payloadCrudo = new String(mensaje.getPayload(), StandardCharsets.UTF_8);

        collection("historial_productos").insertOne(new Document()
                .append("payload", payloadCrudo)
                .append("tipo", "producto")
                .append("createdAt", Instant.now().toString()));

        System.out.println("[Producto - Mongo Write]: " + payloadCrudo);
        return mensaje.ack();
    }

    private MongoCollection<Document> collection(String collectionName) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return database.getCollection(collectionName);
    }
}
