# Gestión de Personas 2.0

Aplicación de escritorio JavaFX + Spring Boot. Incluye el CRUD del manual, búsqueda por nombre/apellido/DNI, validaciones, confirmación de eliminación y páginas de 15 registros. La versión 2 implementa las ampliaciones del capítulo 17 y utiliza PostgreSQL, compatible con Supabase.

## Mejoras implementadas

| Mejora | Comportamiento |
| --- | --- |
| DTO y MapStruct | La interfaz recibe `PersonaDto`; las ediciones no sobrescriben IDs, versiones ni auditoría. |
| Auditoría | Conserva fecha y autor de creación y de la última modificación. Se muestra al seleccionar una persona y se incluye en Excel. |
| Ediciones simultáneas | Una versión por registro impide guardar o eliminar usando datos desactualizados. |
| Flyway | V1 crea personas y V2 agrega auditoría y versiones. Hibernate valida el esquema al iniciar. |
| Excel con Apache POI | Exporta todos los resultados del filtro, con auditoría, encabezados, filtros y fila congelada. Preserva ceros del DNI y trata los valores como texto. |
| PDF con JasperReports | Informe horizontal con filtro, cantidad, fecha, encabezados repetidos y páginas numeradas. |
| Operaciones en segundo plano | Consultas, guardado, eliminación y exportaciones usan `Task`. La búsqueda espera 300 ms y descarta respuestas antiguas. |
| PostgreSQL e importación SQLite | Importación transaccional con respaldo consistente, conservación de IDs y detección de conflictos. |
| Pruebas automatizadas | Negocio, repositorio, interfaz, exportación, Flyway, importación y persistencia entre arranques. GitHub Actions ejecuta la verificación. |

La auditoría identifica al usuario del sistema operativo, o al valor de `APP_AUDIT_USER`. No constituye un inicio de sesión ni un historial completo de versiones. En los datos migrados, la fecha corresponde a la importación; SQLite no almacenaba la fecha original.

## Compilar y probar sin Docker ni Supabase

Se necesita **JDK 17 o 21**. Maven Wrapper está incluido. Los scripts de Windows buscan Java en `JAVA_HOME`, `PATH` y `.jdks` de IntelliJ.

```powershell
. .\scripts\java-env.ps1
Initialize-ProjectJava
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Las pruebas crean PostgreSQL 17 temporal mediante [Embedded Postgres](https://github.com/zonkyio/embedded-postgres), en un puerto libre y con datos aislados. No requieren Docker, Supabase ni instalar PostgreSQL. Maven descarga los binarios de pruebas la primera vez. Las instancias se cierran al terminar y no se incluyen en el instalador. No se modifica el archivo SQLite del usuario.

Se comprueban CRUD, formatos, DNI único también en PostgreSQL, búsqueda literal, paginación, auditoría, versiones obsoletas, persistencia entre arranques, actualización V1 a V2, respaldo SQLite con WAL, importación repetida sin duplicados y reversión ante conflictos. Las pruebas de interfaz verifican que una búsqueda lenta no bloquee la ventana ni sustituya resultados recientes. Las exportaciones se abren y comprueban como archivos XLSX/PDF reales.

Informes y muestras con datos ficticios:

- `target/surefire-reports/`
- `target/vista-personas-v2.png`
- `target/personas-exportacion-prueba.pdf`

JavaFX necesita una sesión gráfica; en Linux sin pantalla utiliza Xvfb. Para probar contra un servidor de pruebas existente, define explícitamente `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD`. Cada clase crea y elimina únicamente su propio esquema `gp_test_...`; esas credenciales necesitan permiso para crear esquemas.

## Ejecutar la aplicación

La versión 2 necesita una conexión PostgreSQL configurada. El motor temporal de pruebas no es la base de uso diario. Compilar y probar no inicia Supabase.

La aplicación lee variables de entorno y, opcionalmente, `%USERPROFILE%\.gestion-personas\database.properties`. Ejemplo para un PostgreSQL que ya esté disponible:

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/postgres?currentSchema=gestion_personas
spring.datasource.username=gestion_personas_app
spring.datasource.password=CONTRASENA_DE_LA_APLICACION
spring.flyway.user=USUARIO_PARA_MIGRACIONES
spring.flyway.password=CONTRASENA_PARA_MIGRACIONES
```

El usuario de Flyway necesita crear/modificar el esquema `gestion_personas`. El usuario de la aplicación necesita uso del esquema, CRUD sobre sus tablas y uso/lectura/actualización de secuencias; esta última permite ajustar los IDs después de importar. Ambos usuarios deben conectarse a la misma base. El script opcional de Supabase prepara estos permisos.

También se admiten `DB_URL`, `DB_USER`, `DB_PASSWORD`, `FLYWAY_USER`, `FLYWAY_PASSWORD` y `APP_AUDIT_USER`. `.env.example` sirve como referencia: la aplicación **no carga `.env` automáticamente**. No guardes credenciales reales en Git ni en los paquetes.

```powershell
.\scripts\run.ps1
# Para seleccionar Java:
.\scripts\run.ps1 -JdkHome 'C:\ruta\al\jdk-21'
```

También puedes ejecutar `Launcher` en IntelliJ, `mvnw.cmd javafx:run` o el JAR:

```powershell
java -jar target/crud-persona-spring-2.0.0.jar
# Comprueba conexión y aplica migraciones pendientes sin abrir la interfaz:
java -jar target/crud-persona-spring-2.0.0.jar --database-check
```

En IntelliJ importa `pom.xml`, utiliza JDK 17/21 y habilita el procesamiento de anotaciones para Lombok y MapStruct. Si falla el arranque, la ventana muestra un aviso para revisar la conexión.

## Supabase local: configuración preparada, ejecución opcional

Se incluyen `supabase/config.toml`, la CLI fijada en `package-lock.json` y `scripts/supabase.ps1`. **El arranque de Docker/Supabase y la migración de datos reales quedan fuera de la ejecución solicitada.** Los siguientes comandos son instrucciones para cuando se decida habilitarlo; compilar, probar y empaquetar no los ejecuta.

Con Docker Desktop y Node.js/npm ya disponibles:

```powershell
.\scripts\supabase.ps1 setup
.\scripts\supabase.ps1 status
# En sesiones posteriores:
.\scripts\supabase.ps1 start
# Para detener conservando datos:
.\scripts\supabase.ps1 stop
```

`setup` prepara una red con puertos vinculados a localhost, inicia el entorno, crea `gestion_personas_app` con contraseña aleatoria y escribe la configuración fuera del repositorio. No sobrescribe una configuración ajena. PostgreSQL usa `127.0.0.1:54322` y Studio `http://127.0.0.1:54323`.

El esquema privado `gestion_personas` no se publica en la API REST de Supabase. La aplicación usa JDBC directamente. Flyway administra sus tablas; las migraciones y semillas de la CLI están desactivadas para evitar dos responsables del mismo esquema. Realtime, funciones Edge, analítica y correo local están desactivados.

Consulta la [documentación oficial de Supabase local](https://supabase.com/docs/guides/local-development). Los scripts están preparados para desarrollo local; una conexión remota requiere sus propias credenciales y configuración de transporte.

## Migrar los registros anteriores de SQLite

Primero compila la versión 2 y configura la base PostgreSQL de destino. Cierra la aplicación anterior antes de importar:

```powershell
# Usa %USERPROFILE%\.gestion-personas\persona.db:
.\scripts\migrate-sqlite.ps1
# O indica otra base SQLite:
.\scripts\migrate-sqlite.ps1 -Source 'C:\ruta\persona.db'
```

El importador abre SQLite en modo lectura y crea un respaldo consistente en `%USERPROFILE%\.gestion-personas\backups`, incluyendo transacciones confirmadas en WAL. Conserva el original, los IDs y los valores. Muestra cuántos registros leyó, importó y encontró ya existentes.

Si se repite con datos idénticos, no duplica registros. Si un ID/DNI existente tiene otros datos, o un registro es inválido, cancela la importación completa y conserva el respaldo. No sobrescribe conflictos automáticamente. Al finalizar ajusta la secuencia para las nuevas altas.

La tabla queda bloqueada para escrituras durante la importación. Los registros importados usan `migracion-sqlite` como autor de auditoría. **Preparar y probar el importador no equivale a haber migrado los datos reales.**

## Datos y validaciones

Nombre y apellido son obligatorios, con hasta 60 caracteres. El DNI es obligatorio, único y tiene exactamente 8 dígitos. Correo y teléfono son opcionales; el correo admite hasta 120 caracteres y debe tener formato válido, y el teléfono informado debe tener 9 dígitos. El servicio valida antes de guardar y PostgreSQL añade restricciones de integridad.

La búsqueda no distingue mayúsculas y trata `%`, `_` y `\` como caracteres literales. La exportación incluye **todos** los resultados del filtro, aunque ocupen varias páginas. Los archivos se escriben primero en un temporal: una exportación fallida no sustituye el archivo anterior.

## Generar instaladores

```powershell
# Ejecuta compilación y todas las pruebas antes de empaquetar:
.\scripts\package.ps1 -Type exe
# Reutiliza un JAR ya comprobado:
.\scripts\package.ps1 -Type exe -SkipBuild
.\scripts\package.ps1 -Type app-image -SkipBuild
```

EXE/MSI requieren [WiX Toolset 3.11](https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm) en `PATH`, `.tools/wix311` o mediante `-WixHome`. La aplicación portable no necesita WiX. Se generan:

- `instalador/GestionPersonas-2.0.0.exe`
- `instalador/portable-2.0.0/GestionPersonas/GestionPersonas.exe`
- Opcionalmente, `instalador/GestionPersonas-2.0.0.msi` con `-Type msi`.

Los paquetes incluyen Java y las bibliotecas. **No incluyen PostgreSQL, Docker, Supabase, credenciales ni registros de personas**; la conexión se configura por separado. Conserva toda la carpeta portable. Los paquetes existentes no se sobrescriben.

Para macOS/Linux se empaqueta desde esos sistemas con sus bibliotecas JavaFX. Consulta [jpackage para Java 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

## Estructura

| Responsabilidad | Archivos |
| --- | --- |
| Arranque | `Launcher`, `JavaFxApplication`, `CrudPersonaApplication` |
| DTO y mapeo | `dto/PersonaDto`, `mapper/PersonaMapper` |
| Entidad y persistencia | `model/Persona`, `repository/PersonaRepository` |
| Negocio y exportación | `service/PersonaService`, `service/PersonaExportService` |
| Importación | `service/SqliteMigrationService`, `scripts/migrate-sqlite.ps1` |
| Auditoría y tareas | `config/AuditoriaConfig`, `config/TareasConfig` |
| Vista | `controller/PersonaController`, `view/persona-view.fxml`, `css/app.css` |
| Esquema y conexión | `db/migration/V*.sql`, `application.properties`, `supabase/config.toml` |
| Verificación y distribución | `src/test`, `.github/workflows/verify.yml`, `scripts/package.ps1` |

Versiones principales: Spring Boot 3.3.4, JavaFX 21.0.4, AtlantaFX 2.0.1, MapStruct 1.6.3, Flyway 10.20.1, Apache POI 5.5.1 y JasperReports 7.0.3. SQLite JDBC se conserva exclusivamente para importar bases anteriores.
