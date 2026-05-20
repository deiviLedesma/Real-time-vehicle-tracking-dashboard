#!/bin/sh

set -eu

wait_for_mongo() {
  uri="$1"
  label="$2"

  until mongosh --quiet "$uri" --eval "db.adminCommand({ ping: 1 }).ok" >/dev/null 2>&1; do
    echo "Esperando a $label..."
    sleep 2
  done
}

init_rs() {
  uri="$1"
  replset="$2"
  member="$3"
  extra="$4"

  mongosh --quiet "$uri" <<EOF
try {
  rs.status();
} catch (e) {
  rs.initiate({
    _id: "$replset",
    $extra
    members: [{ _id: 0, host: "$member" }]
  });
}
EOF
}

wait_for_primary() {
  uri="$1"
  label="$2"

  until mongosh --quiet "$uri" --eval "db.hello().isWritablePrimary" | grep -q true; do
    echo "Esperando primario de $label..."
    sleep 2
  done
}

wait_for_mongo "mongodb://mongo-configsvr:27019/admin" "config server"
wait_for_mongo "mongodb://mongo-shard1:27018/admin" "shard 1"
wait_for_mongo "mongodb://mongo-shard2:27018/admin" "shard 2"

init_rs "mongodb://mongo-configsvr:27019/admin" "configReplSet" "mongo-configsvr:27019" "configsvr: true,"
init_rs "mongodb://mongo-shard1:27018/admin" "shard1ReplSet" "mongo-shard1:27018" ""
init_rs "mongodb://mongo-shard2:27018/admin" "shard2ReplSet" "mongo-shard2:27018" ""

wait_for_primary "mongodb://mongo-configsvr:27019/admin" "configReplSet"
wait_for_primary "mongodb://mongo-shard1:27018/admin" "shard1ReplSet"
wait_for_primary "mongodb://mongo-shard2:27018/admin" "shard2ReplSet"
wait_for_mongo "mongodb://mongo-router:27017/admin" "mongos"

mongosh --quiet "mongodb://mongo-router:27017/admin" <<'EOF'
const shardStatus = sh.status();
const shardNames = (shardStatus.shards || []).map((shard) => shard._id);

if (!shardNames.includes("shard1ReplSet")) {
  sh.addShard("shard1ReplSet/mongo-shard1:27018");
}

if (!shardNames.includes("shard2ReplSet")) {
  sh.addShard("shard2ReplSet/mongo-shard2:27018");
}

sh.enableSharding("mvts_almacenamiento");

const dbRef = db.getSiblingDB("mvts_almacenamiento");
const configDb = db.getSiblingDB("config");

if (!dbRef.getCollectionInfos({ name: "historial_congestiones" }).length) {
  dbRef.createCollection("historial_congestiones");
}

if (!dbRef.getCollectionInfos({ name: "historial_productos" }).length) {
  dbRef.createCollection("historial_productos");
}

if (!configDb.collections.findOne({ _id: "mvts_almacenamiento.historial_congestiones" })) {
  sh.shardCollection("mvts_almacenamiento.historial_congestiones", { _id: "hashed" });
}

if (!configDb.collections.findOne({ _id: "mvts_almacenamiento.historial_productos" })) {
  sh.shardCollection("mvts_almacenamiento.historial_productos", { _id: "hashed" });
}
EOF

echo "Sharding de Mongo configurado."
