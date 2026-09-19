# Gestión de Personas 2.1 — SQLite

Aplicación de escritorio con JavaFX y Spring Boot. Funciona con un archivo SQLite local: **no requiere PostgreSQL, Supabase, Docker, Node.js ni credenciales de base de datos**.

## Ejecutar

Se necesita JDK 17 o 21 para desarrollar. Maven Wrapper está incluido y los scripts buscan Java en `JAVA_HOME`, `PATH` y `.jdks` de IntelliJ.

```powershell
.\scripts\run.ps1
# Para elegir Java:
.\scripts\run.ps1 -JdkHome 'C:\ruta\al\jdk-21'
```

También puedes ejecutar `Launcher` desde IntelliJ o `mvnw.cmd javafx:run`. En IntelliJ importa `pom.xml` y habilita el procesamiento de anotaciones para Lombok y MapStruct.

La base se guarda en `%USERPROFILE%\.gestion-personas\persona.db`, fuera del proyecto y de la instalación. Los registros se conservan al cerrar y volver a abrir. La versión 2.1 vuelve a utilizar el mismo archivo que la versión SQLite original.

## Actualización de la base existente

En el primer arranque, Flyway reconoce la estructura SQLite anterior, crea un **respaldo consistente** en la carpeta `backups` situada junto al archivo y añade auditoría, control de versiones y restricciones de integridad. Conserva IDs, nombres, apellidos, DNI, correos y teléfonos. El respaldo incluye transacciones confirmadas en WAL.

La actualización se realiza en una transacción. Si hay DNI duplicados o datos incompatibles, se cancela sin sustituir la tabla original y se conserva el respaldo. No se eliminan ni corrigen registros automáticamente. Una estructura anterior desconocida también se rechaza sin modificarla. Cierra otras instancias de la aplicación antes de actualizar.

Los registros antiguos reciben el autor `migracion-sqlite` y la fecha de actualización del esquema; la aplicación anterior no almacenaba sus fechas de creación. Los arranques posteriores no repiten la migración ni generan respaldos adicionales si no hay cambios de esquema pendientes.

Flyway administra el esquema; Hibernate lo valida. Las migraciones están en `src/main/resources/db/migration`. No deben editarse después de aplicarlas a una base distribuida: los cambios siguientes se añaden en una nueva versión.

## Funciones y mejoras

| Función | Comportamiento |
| --- | --- |
| CRUD | Alta, consulta, edición y eliminación con confirmación. |
| Búsqueda y páginas | Nombre, apellido o DNI; páginas de 15 registros. No distingue mayúsculas, incluidas letras con tilde. `%`, `_` y `\` se buscan literalmente. |
| DTO y MapStruct | La interfaz recibe DTO; una edición no sobrescribe el ID, la auditoría ni la versión interna. |
| Auditoría | Fecha y autor de creación y última modificación, visibles al seleccionar una persona y en Excel. |
| Ediciones simultáneas | Impide guardar o eliminar con una versión antigua del registro. Se debe actualizar la tabla. |
| Excel | Exporta todos los resultados del filtro, con auditoría, filtros y encabezados congelados. Conserva ceros del DNI y no interpreta fórmulas introducidas en los datos. |
| PDF | JasperReports genera un informe horizontal con filtro, cantidad, fecha y páginas numeradas. |
| Segundo plano | Consultas, guardado, borrado y exportaciones usan `Task`. Una respuesta de búsqueda antigua no reemplaza la más reciente. |
| Escritura de archivos | Una exportación fallida conserva el archivo anterior; el reemplazo se realiza al finalizar. |

La auditoría utiliza el usuario del sistema operativo o `APP_AUDIT_USER`. No constituye un sistema de inicio de sesión ni un historial completo de todas las versiones.

Nombre y apellido son obligatorios y admiten hasta 60 caracteres. El DNI es obligatorio, único y tiene 8 dígitos. Correo y teléfono son opcionales; el correo debe tener formato válido y hasta 120 caracteres, y el teléfono informado debe tener 9 dígitos. El servicio y la base aplican las restricciones correspondientes.

## Configuración opcional

No se necesita configurar nada para usar la ubicación predeterminada. Para elegir otra base, crea primero su carpeta y define `SQLITE_URL`:

```powershell
$env:SQLITE_URL = 'jdbc:sqlite:C:/datos/persona.db'
.\scripts\run.ps1
```

También puedes crear `%USERPROFILE%\.gestion-personas\sqlite.properties`:

```properties
spring.datasource.url=jdbc:sqlite:C:/datos/persona.db
```

La configuración `database.properties` y las variables `DB_URL`, `DB_USER`, `DB_PASSWORD` y `FLYWAY_*` utilizadas por la versión PostgreSQL ya no se cargan desde la configuración de esta versión. `.env.example` es solo una referencia; `.env` no se carga automáticamente.

SQLite utiliza una conexión compartida y espera hasta diez segundos si el archivo está ocupado. La aplicación puede usarse sin conexión a Internet después de compilar o instalar.

## Compilar y verificar

```powershell
. .\scripts\java-env.ps1
Initialize-ProjectJava
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Las pruebas usan archivos SQLite independientes en `target/test-databases`; nunca la base del usuario. Cubren CRUD, validaciones, DNI único, búsqueda, paginación, auditoría, versiones obsoletas, persistencia entre arranques, actualización de una base anterior, respaldo con WAL y reversión ante conflictos. También comprueban la interfaz y el contenido de los archivos Excel/PDF. GitHub Actions ejecuta la verificación en Windows.

JavaFX necesita una sesión gráfica; en Linux sin pantalla utiliza Xvfb. Informes y muestras con datos ficticios:

- `target/surefire-reports/`
- `target/vista-personas-v2.png`
- `target/personas-exportacion-prueba.pdf`

El JAR se genera en `target/crud-persona-spring-2.1.0.jar`:

```powershell
java -jar target/crud-persona-spring-2.1.0.jar
# Comprueba la base y aplica actualizaciones pendientes sin abrir la interfaz:
java -jar target/crud-persona-spring-2.1.0.jar --database-check
```

## Instalador y aplicación portable

```powershell
.\scripts\package.ps1 -Type exe
# Reutiliza un JAR ya comprobado:
.\scripts\package.ps1 -Type exe -SkipBuild
.\scripts\package.ps1 -Type app-image -SkipBuild
```

EXE/MSI requieren [WiX 3.11](https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm) en `PATH`, `.tools/wix311` o mediante `-WixHome`. La versión portable no requiere WiX. Se generan:

- `instalador/GestionPersonas-2.1.0.exe`
- `instalador/portable-2.1.0/GestionPersonas/GestionPersonas.exe`
- Opcionalmente, MSI con `-Type msi`.

Ambos paquetes incluyen Java y SQLite. **No incluyen registros del usuario**. Conserva toda la carpeta portable. Los paquetes anteriores no se sobrescriben; utiliza la versión 2.1 para volver a SQLite.

Los paquetes para macOS/Linux se generan desde esos sistemas con sus bibliotecas JavaFX. Consulta [jpackage para Java 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

Versiones principales: Spring Boot 3.3.4, JavaFX 21.0.4, SQLite JDBC 3.45.3.0, AtlantaFX 2.0.1, MapStruct 1.6.3, Flyway 10.20.1, Apache POI 5.5.1 y JasperReports 7.0.3.
