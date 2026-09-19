CREATE TABLE persona (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre VARCHAR(60) NOT NULL,
    apellido VARCHAR(60) NOT NULL,
    dni VARCHAR(8) NOT NULL,
    email VARCHAR(120),
    telefono VARCHAR(9)
);
