# Servicio de catálogo y personas

Servicio REST desarrollado con Spring Boot para administrar personas y consultar el catálogo de productos de GestoPago. El catálogo se almacena en PostgreSQL y MongoDB, y puede consultarse desde una fuente local o desde la API externa como último respaldo.

## Características

- API REST para crear, actualizar y eliminar personas.
- Consulta del catálogo mediante `GET /productos`.
- Integración con GestoPago mediante OpenFeign.
- Renovación programada del token de GestoPago.
- Sincronización programada del catálogo en PostgreSQL y MongoDB.
- Migraciones de PostgreSQL administradas con Flyway.
- Fallback de catálogo: MongoDB, PostgreSQL y API externa.
- Documentación OpenAPI/Swagger y endpoint de Actuator proporcionados por Spring Boot.
- Pruebas unitarias con JUnit 5, Mockito y H2.

## Tecnologías

| Tecnología | Versión o uso |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.3.6 |
| Gradle | Wrapper incluido |
| PostgreSQL | Base relacional y migraciones Flyway |
| MongoDB | Persistencia principal del catálogo |
| GestoPago | Proveedor externo de autenticación y productos |
| OpenFeign | Cliente HTTP hacia GestoPago |
| MapStruct / Lombok | Mapeo y reducción de código repetitivo |

## Requisitos previos

- JDK 17 configurado en `JAVA_HOME`.
- PostgreSQL disponible en `localhost:5432`.
- MongoDB disponible en `localhost:27017`.
- Credenciales válidas para la API de GestoPago.
- Acceso de red al host configurado en `gestopago.auth.url`.

La aplicación crea y actualiza las tablas de PostgreSQL mediante Flyway. La base de datos configurada por defecto es `EnviromentProject`; debe existir antes de iniciar la aplicación.

## Configuración

La configuración se encuentra en `src/main/resources/application.properties`. Para despliegues reales, sobrescriba los valores sensibles mediante variables de entorno o un mecanismo de configuración externo. Spring Boot permite sustituir cualquier propiedad, por ejemplo:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/EnviromentProject"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
$env:SPRING_DATA_MONGODB_URI = "mongodb://localhost:27017/EnviromentProject"
$env:GESTOPAGO_AUTH_URL = "https://gestopago.portalventas.net"
$env:GESTOPAGO_AUTH_ID_DISTRIBUIDOR = "83"
$env:GESTOPAGO_AUTH_CODIGO_DISPOSITIVO = "<codigo-dispositivo>"
$env:GESTOPAGO_AUTH_PASSWORD = "<password>"
$env:GESTOPAGO_AUTH_TOKEN_ESTATICO = "<token-opcional>"
```

Propiedades relevantes:

| Propiedad | Descripción | Valor predeterminado del proyecto |
| --- | --- | --- |
| `spring.datasource.url` | URL de PostgreSQL | `jdbc:postgresql://localhost:5432/EnviromentProject` |
| `spring.data.mongodb.uri` | URI de MongoDB | `mongodb://localhost:27017/EnviromentProject` |
| `gestopago.auth.url` | URL base de GestoPago | `https://gestopago.portalventas.net` |
| `gestopago.auth.refresh-rate-ms` | Frecuencia de renovación del token | `3600000` ms |
| `gestopago.sync.cron` | Cron de sincronización del catálogo | `0 0 0 * * ?` |
| `spring.cloud.openfeign.client.config.default.connect-timeout` | Timeout de conexión | `3000` ms |
| `spring.cloud.openfeign.client.config.default.read-timeout` | Timeout de lectura | `5000` ms |

> **Seguridad:** el archivo de configuración del proyecto contiene credenciales y un token de GestoPago. No los publiques ni los uses en otros entornos. Rota esos valores y mantenlos fuera del control de versiones antes de desplegar.

## Ejecución local

Desde la raíz del proyecto:

```powershell
./gradlew.bat bootRun
```

En una terminal Unix:

```bash
./gradlew bootRun
```

La aplicación escucha en el puerto `8080` por defecto. Para cambiarlo:

```powershell
$env:SERVER_PORT = "8081"
./gradlew bootRun
```

También puede generar el artefacto ejecutable:

```powershell
./gradlew.bat clean build
java -jar build/libs/prueba-1.0.jar
```

El nombre exacto del JAR puede variar según la configuración de Gradle.

## API REST

### Crear una persona

`POST /personas`

```bash
curl -X POST http://localhost:8080/personas \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana","apellidoP":"García","apellidoMaterno":"López"}'
```

Respuesta de ejemplo:

```json
{
  "codigo": 0,
  "mensaje": "Operación realizada correctamente"
}
```

### Actualizar una persona

`PUT /personasActualiza`

```bash
curl -X PUT http://localhost:8080/personasActualiza \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana","apellidoP":"García","apellidoMaterno":"Martínez"}'
```

### Eliminar una persona

`PUT /personasElimina`

```bash
curl -X PUT http://localhost:8080/personasElimina \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ana"}'
```

### Consultar productos

`GET /productos`

```bash
curl http://localhost:8080/productos
```

La respuesta contiene un mensaje y la lista de productos. El servicio intenta obtenerlos en este orden:

1. MongoDB, filtrando productos activos.
2. PostgreSQL, como respaldo.
3. API externa de GestoPago, si las bases locales no están disponibles o están vacías.

Los errores de integración se responden con HTTP y códigos de aplicación específicos:

| Código | HTTP | Situación |
| --- | --- | --- |
| `91` | 502 | Fallo de autenticación con GestoPago |
| `92` | 504 | Timeout del proveedor |
| `93` | 503 | Error de comunicación |
| `94` | 502 | Respuesta no exitosa del proveedor |
| `99` | 503 | Catálogo no disponible |
| `500` | 500 | Error interno no controlado |

## Procesos programados

### Renovación del token

`GestoPagoTokenServiceImpl` renueva el token al iniciar la aplicación y, por defecto, cada hora (`gestopago.auth.refresh-rate-ms`). El token se guarda en la tabla `gestopago_tokens`. Si no existe en la base de datos, puede utilizarse `gestopago.auth.token-estatico` como respaldo.

### Sincronización del catálogo

`GestoPagoCatalogSyncTask` consulta `getProductList` con el token activo y persiste el catálogo en PostgreSQL y MongoDB. Por defecto se ejecuta cada minuto (`0 * * * * ?`).

Antes de actualizar:

- descarta productos sin identificador o con precio negativo;
- aborta si la respuesta está vacía;
- aborta si el catálogo nuevo representa una reducción superior al 50 % respecto a MongoDB;
- marca como inactivos los productos que ya no aparecen en la respuesta.

Para una ejecución diaria a medianoche, por ejemplo:

```properties
gestopago.sync.cron=0 0 0 * * ?
```

## Base de datos y migraciones

Flyway ejecuta estas migraciones al iniciar:

- `V1__create_gestopago_tokens.sql`: tokens de autenticación.
- `V2__create_personas.sql`: personas.
- `V3__create_gestopago_productos.sql`: catálogo relacional.

MongoDB utiliza la colección correspondiente a `ProductoDocument` para mantener una copia del catálogo.

## Pruebas y calidad

Ejecutar todas las pruebas:

```powershell
./gradlew.bat test
```

Generar cobertura JaCoCo:

```powershell
./gradlew.bat test jacocoTestReport
```

El reporte XML queda en `build/reports/jacoco/test/jacocoTestReport.xml` cuando la tarea se ejecuta correctamente.

Las pruebas actuales cubren principalmente el servicio de productos y la tarea de sincronización. La integración real con PostgreSQL, MongoDB y GestoPago requiere servicios disponibles y credenciales válidas.

## Documentación OpenAPI y monitoreo

Con la aplicación iniciada, Springdoc expone habitualmente:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Especificación OpenAPI: `http://localhost:8080/v3/api-docs`
- Actuator: `http://localhost:8080/actuator`

Las rutas exactas pueden cambiar si se sobrescriben las propiedades de Springdoc o Actuator.

## Estructura principal

```text
src/
  main/
    java/com/proyecto/servicios/
      client/       Clientes Feign para GestoPago
      config/       Configuración de base de datos y OpenAPI
      controller/   Endpoints REST
      entity/       Entidades JPA y documentos MongoDB
      exception/    Excepciones y manejador global
      mapper/       Mapeos de objetos
      model/        DTOs de entrada y salida
      repositorys/  Repositorios PostgreSQL y MongoDB
      service/      Lógica de negocio
      tasks/        Procesos programados
    resources/
      db/migration/ Migraciones Flyway
      application.properties
  test/             Pruebas automatizadas
```

## Docker

El `build.gradle` declara las tareas `dockerImage` y `dockerUp`, que esperan un `Dockerfile` y una configuración de Docker Compose en la raíz del proyecto:

```powershell
./gradlew.bat dockerImage
./gradlew.bat dockerUp
```

Verifique que esos archivos y Docker Desktop estén disponibles antes de usar las tareas. Las tareas no sustituyen la configuración de PostgreSQL, MongoDB ni las credenciales de GestoPago.

## Licencia

No se ha definido una licencia en el repositorio.
