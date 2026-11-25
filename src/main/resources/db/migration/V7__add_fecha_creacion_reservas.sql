-- Agregar columna fecha_creacion para control de expiración de 15 minutos en reservas PENDIENTE
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
