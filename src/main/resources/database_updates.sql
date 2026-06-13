-- Script para actualizar usuarios operarios con la contraseña correcta
-- BCrypt hash de "dev-password-123"

-- Eliminar el usuario de servicio ms-liquidador-neg si existe
DELETE FROM user WHERE username = 'ms-liquidador-neg';

-- Actualizar todos los usuarios operarios (rol_user_ide = 3) con la contraseña correcta
UPDATE user
SET password = '$2a$10$slPvTTm1S5hy8zyL0X8Ige5h1KwHTvG4/Hln6Yx2vU8TZ5PBMqKha'
WHERE rol_user_ide = 3 AND state_user_id = 1;

-- Verificar los cambios
SELECT ide_user, username, password, rol_user_ide, state_user_id
FROM user
WHERE rol_user_ide = 3 AND state_user_id = 1;