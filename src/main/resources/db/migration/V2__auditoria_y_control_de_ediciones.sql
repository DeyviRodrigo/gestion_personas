-- Reconstrucción transaccional: también incorpora restricciones a la base SQLite anterior.
CREATE TABLE persona_actualizada (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre VARCHAR(60) NOT NULL CHECK (length(trim(nombre)) BETWEEN 1 AND 60),
    apellido VARCHAR(60) NOT NULL CHECK (length(trim(apellido)) BETWEEN 1 AND 60),
    dni VARCHAR(8) NOT NULL CHECK (length(dni) = 8 AND dni NOT GLOB '*[^0-9]*'),
    email VARCHAR(120) CHECK (email IS NULL OR length(email) <= 120),
    telefono VARCHAR(9) CHECK (telefono IS NULL OR telefono = '' OR (length(telefono) = 9 AND telefono NOT GLOB '*[^0-9]*')),
    creado_en TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP NOT NULL,
    creado_por VARCHAR(120) NOT NULL,
    actualizado_por VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO persona_actualizada (id,nombre,apellido,dni,email,telefono,creado_en,actualizado_en,creado_por,actualizado_por,version)
SELECT id,nombre,apellido,dni,email,telefono,
       strftime('%Y-%m-%d %H:%M:%f','now'),strftime('%Y-%m-%d %H:%M:%f','now'),
       'migracion-sqlite','migracion-sqlite',0
FROM persona;

CREATE UNIQUE INDEX uk_persona_dni_nueva ON persona_actualizada (dni);
DROP TABLE persona;
ALTER TABLE persona_actualizada RENAME TO persona;
DROP INDEX uk_persona_dni_nueva;
CREATE UNIQUE INDEX uk_persona_dni ON persona (dni);
CREATE INDEX idx_persona_actualizado_en ON persona (actualizado_en);
