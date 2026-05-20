USE mvts_topologia;

CREATE TABLE IF NOT EXISTS semaforos (
    id VARCHAR(20) NOT NULL PRIMARY KEY,
    latitud DOUBLE NOT NULL,
    longitud DOUBLE NOT NULL,
    sentido VARCHAR(30) NOT NULL
);

INSERT INTO semaforos (id, latitud, longitud, sentido) VALUES
    ('01', 27.481, -109.931, 'NORTE'),
    ('02', 27.482, -109.932, 'ESTE'),
    ('03', 27.483, -109.933, 'SUR')
ON DUPLICATE KEY UPDATE
    latitud = VALUES(latitud),
    longitud = VALUES(longitud),
    sentido = VALUES(sentido);
