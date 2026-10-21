# restaurante-backend-java

Backend de operaciones de la Cevichería "La Casa Blanca" — API REST con autenticación JWT, gestión de mesas, pedidos y reportes.

## Stack

- Java 17
- Spring Boot 3 (3.5.16)
- Spring Web, Spring Data JPA, Spring Security, Validation
- JJWT 0.13.0 (JWT)
- PostgreSQL (producción), H2 (tests)
- Maven (wrapper `mvnw.cmd`), JUnit 5 + Mockito

> **Nota (2026-10-09):** Spring Boot 3.5.16 es la última versión 3.x y está en fin de soporte OSS desde el 30/06/2026. Se mantiene por exigencia del brief (Spring Boot 3).

## Cómo ejecutar

Requisitos: JDK 17+ en el PATH (verificado con JDK 21). No hace falta instalar Maven: se usa el wrapper.

Comandos realmente ejecutados en esta tarea:

```powershell
.\mvnw.cmd -v            # versión del wrapper (Maven 3.9.16)
.\mvnw.cmd clean test    # compila y corre los tests (perfil test: H2 en memoria)
.\mvnw.cmd clean package  # genera backend-0.0.1-SNAPSHOT.jar en target\
```

### Variables de entorno

El arranque real contra PostgreSQL necesita estas variables (ejemplos en `.env.example`).
Spring Boot **no** lee `.env` por sí solo; expórtalas en PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/lacasablanca"
$env:DB_USER = "usuario_ejemplo"
$env:DB_PASSWORD = "cambiar_esto"
$env:JWT_SECRET = "cambiar_esto_por_un_secreto_largo"
$env:JWT_EXPIRATION_MS = "86400000"
.\mvnw.cmd spring-boot:run
```

> **NO VERIFICADO (PE-6):** el arranque contra PostgreSQL real no se ejecutó en esta
> tarea (no hay PostgreSQL instalado en la máquina). Los tests corren con H2 en memoria.

## Equipo

- Max Erick Munoz Paredes — Backend Lead (Java)
- David Gabriel Rojas Garcia — Mobile Lead (Kotlin)
- Julio Alexander Perez Muñoz — QA & DevOps Lead
- Jerardo Cutipa — Backoffice Django Lead
