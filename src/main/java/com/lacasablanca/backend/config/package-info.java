/**
 * Configuracion transversal: Spring Security (JWT, reglas 401/403),
 * Jackson (snake_case) y beans de infraestructura.
 *
 * <p>Sin secretos hardcodeados: todo llega por variables de entorno
 * ({@code JWT_SECRET}, {@code JWT_EXPIRATION_MS}) via {@code application.yml}.</p>
 */
package com.lacasablanca.backend.config;
