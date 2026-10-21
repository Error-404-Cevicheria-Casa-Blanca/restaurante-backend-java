/**
 * Servicios (@Service) con la logica de negocio.
 *
 * <p>Cada caso de uso (login, gestion de mesas, pedidos) vive aqui y se
 * orquesta sobre los repositories. Las operaciones que escriben mas de una
 * vez se anotan con {@code @Transactional}.</p>
 */
package com.lacasablanca.backend.service;
