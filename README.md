# Servicio de clientes y cuentas

API REST con Spring Boot, PostgreSQL, JPA, BCrypt y JWT.

## Configuración

La conexión PostgreSQL se construye con `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USERNAME` y `DB_PASSWORD`; localmente tienen valores por defecto excepto
la contraseña. MongoDB se configura con `MONGODB_URI`. Antes de iniciar la
aplicación, define `JWT_SECRET` con al menos 32 bytes aleatorios. También se
acepta una clave codificada en Base64 con el prefijo `base64:`. El token dura
una hora por defecto (`app.jwt.expiration-ms`).

En PowerShell puedes generar una clave aleatoria temporal y arrancar la
aplicación desde la misma terminal:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
.\gradlew.bat bootRun --args="--spring.profiles.active=academico"
```

La variable temporal se pierde al cerrar esa terminal. No guardes el secreto
en `application.properties` ni en Git. Si cambias la clave, los JWT firmados
con la anterior dejarán de ser válidos.

La contraseña y el token estático de GestoPago también se leen desde las
variables `GESTOPAGO_PASSWORD` y `GESTOPAGO_STATIC_TOKEN`, respectivamente.
El perfil `academico` desactiva las tareas automáticas de GestoPago para que
el proyecto pueda arrancar sin acceso al proveedor externo.

Si la contraseña de GestoPago ya apareció en un log o se compartió, solicita
su rotación al proveedor. Configura la credencial nueva solo en el entorno
local o en el gestor de secretos correspondiente; la llamada actual conserva
el formato requerido por la integración, pero los logs de la aplicación ya no
registran la URL ni el detalle de la excepción.

El saldo de apertura se configura con `bank.account.initial-balance`; por
defecto es `0.00` y no puede ser negativo.

**Aviso:** Flyway aplicará `V4__create_client_banking_schema.sql` al iniciar. Esa
migración recrea `personas`, `contactos`, `direccion`, `informacion_laboral`,
`cuenta_bancaria` y `usuarios`, por lo que elimina los datos previos de esas
tablas, siguiendo el DDL proporcionado. No se debe aplicar sobre datos que
necesiten conservarse. `V5__strengthen_client_data_constraints.sql` agrega
restricciones de formato y unicidad normalizada; fallará explícitamente si ya
hay datos duplicados o inválidos. Antes de aplicar migraciones, confirma la base
y esquema destino y realiza un respaldo. Si Flyway informa un checksum distinto
para V4, no borres el historial ni ejecutes `repair` sin reconciliar primero el
archivo aplicado y el esquema real. Flyway no crea baselines automáticamente:
si encuentra tablas existentes sin historial, se detendrá para evitar ejecutar
migraciones destructivas sobre una base cuya versión no está confirmada.

## API

`POST /clientes` y `POST /auth/login` son públicos. Las demás rutas requieren
`Authorization: Bearer <token>`. El endpoint de creación recibe correo y
contraseña; al completar el registro crea de forma transaccional el cliente,
su información asociada, una cuenta activa y el usuario con contraseña BCrypt.

- Clientes: `POST /clientes`, `GET /clientes`, `GET /clientes/{id}`,
  `PUT /clientes/{id}`, `DELETE /clientes/{id}` (baja lógica).
- Consultas de clientes: `GET /clientes/activos`,
  `/clientes/curp/{curp}`, `/clientes/rfc/{rfc}`,
  `/clientes/correo/{correo}`, `/clientes/cuenta/{numeroCuenta}` y
  `/clientes/registrados?desde=YYYY-MM-DD&hasta=YYYY-MM-DD`.
- Cuentas: `GET /cuentas/{numeroCuenta}`, `/cuentas/activas` y
  `/cuentas/{numeroCuenta}/saldo`.
- Los listados aceptan `pagina` (desde 0) y `tamano` (1-100, predeterminado
  20).
- Para registrar clientes, configura `BANK_CLABE_INSTITUTION_CODE` y
  `BANK_CLABE_PLAZA_CODE` con los códigos CLABE reales de 3 dígitos. La CLABE
  se genera con dígito verificador; el alta falla explícitamente si faltan.
- Para la demostración académica local, el perfil `academico` configura códigos
  ficticios (`999` y `001`) para probar la estructura y el dígito verificador:
  `.\gradlew.bat bootRun --args="--spring.profiles.active=academico"`.
  Esos valores no identifican una institución bancaria real y sus CLABE no
  deben usarse para transferencias. En otros entornos, define los códigos
  oficiales como variables `BANK_CLABE_INSTITUTION_CODE` y
  `BANK_CLABE_PLAZA_CODE`; no los guardes en el repositorio.
- Acceso: `POST /auth/login`.
- Usuario autenticado: `GET /usuarios/{id}`, `GET /usuarios/{id}/perfil`,
  `PUT /usuarios/{id}/password` y `DELETE /usuarios/{id}` para desactivar su
  propio acceso. Los cambios de perfil y la baja de cliente solo se permiten
  para el propio usuario.
- Un usuario desactivado que ingresa con sus credenciales válidas recibe un
  JWT de modo `RECOVERY`, limitado a consultar su propio perfil y ejecutar
  `PUT /usuarios/{id}/reactivar`. El token de recuperación no autoriza otras
  rutas; después de reactivarse, debe iniciar sesión otra vez para obtener un
  JWT de modo `FULL`. La baja de un cliente también puede restaurarse por este
  flujo, lo que reactiva sus cuentas conforme a la regla del negocio.

Swagger UI está disponible en `/swagger-ui/index.html`.

## Despliegue académico en Render con Docker

El repositorio incluye un `Dockerfile` multi-stage para compilar y ejecutar la
aplicación con Java 17, un `.dockerignore` y un `render.yaml` (Blueprint) que
define un servicio web Docker y una base PostgreSQL de Render. El perfil
`render` configura códigos CLABE ficticios y desactiva las tareas programadas
de GestoPago; **esta configuración es únicamente para demostración académica y
no debe usarse para operar con dinero real**.

1. Sube estos cambios a la rama que quieras desplegar en GitHub.
2. En Render selecciona **New → Blueprint**, conecta ese repositorio y confirma
   el Blueprint `render.yaml`. Render creará el servicio Docker y PostgreSQL.
3. Render pedirá `MONGODB_URI` durante la configuración inicial del Blueprint.
   Proporciona la URI de MongoDB Atlas, con un usuario de base de datos y acceso
   de red configurados para Render. No uses `localhost` para una base remota.
4. Espera el build y el deploy. La aplicación escucha el puerto `PORT` que
   Render proporciona y expone `/actuator/health` como health check.

El Blueprint usa planes gratuitos para la demostración. Render puede suspender
la aplicación por inactividad; su PostgreSQL gratuito tiene 1 GB y expira 30
días después de crearse, con eliminación posterior si no se actualiza el plan.
No almacenes información importante ahí sin un plan persistente y respaldos.
La base declarada en el Blueprint es nueva: no la sustituyas por una base con
datos que deban conservarse, ya que V4 contiene operaciones destructivas.

Render genera `JWT_SECRET` automáticamente. Las credenciales opcionales de
GestoPago, si luego se habilita esa integración, deben definirse como secretos
del servicio (`GESTOPAGO_PASSWORD` y `GESTOPAGO_STATIC_TOKEN`), no en Git.
El endpoint público de registro y la ausencia de roles hacen que esta
configuración no sea apropiada para producción.
