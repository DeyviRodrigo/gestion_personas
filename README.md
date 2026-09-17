# Gestión de Personas

Aplicación de escritorio del manual **CRUD de Personas con JavaFX y Spring Boot**: altas, consulta, edición y eliminación con confirmación, búsqueda por nombre/apellido/DNI, validaciones y páginas de 15 registros. Usa JavaFX 21.0.4, AtlantaFX 2.0.1, Spring Boot 3.3.4, JPA, Lombok y SQLite 3.45.3.0.

## Ejecutar en Windows

Se necesita un **JDK 17 o 21** para desarrollar. El proyecto incluye Maven Wrapper; no hace falta instalar Maven. Los scripts buscan el JDK en `JAVA_HOME`, `PATH` y la carpeta `.jdks` de IntelliJ.

Desde la raíz del proyecto, en PowerShell:

```powershell
.\scripts\run.ps1
```

Para indicar un JDK concreto:

```powershell
.\scripts\run.ps1 -JdkHome 'C:\ruta\al\jdk-21'
```

También puedes ejecutar `Launcher` desde IntelliJ o, con `JAVA_HOME` configurado:

```powershell
.\mvnw.cmd javafx:run
```

En IntelliJ importa `pom.xml`, selecciona JDK 17/21 y activa **Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing**. El paquete usado aquí es `com.example.crudpersona`; Maven, FXML y clases están alineados con él.

## Datos y validaciones

La aplicación crea automáticamente `%USERPROFILE%\.gestion-personas\persona.db`. Así puede guardar datos después de instalarse y conserva los registros al cerrar y reabrir. La base antigua `persona.db` de la raíz no se borra ni se sobrescribe. Si contiene datos de una versión anterior, cierra la aplicación y copia ese archivo a la nueva ubicación antes del primer uso; no reemplaces una base que ya contenga datos sin respaldarla.

El nombre y el apellido son obligatorios y admiten hasta 60 caracteres. El DNI es obligatorio, tiene exactamente 8 dígitos y es único. El correo es opcional, tiene formato de correo y hasta 120 caracteres. El teléfono es opcional y, si se indica, debe contener 9 dígitos.

Las validaciones se ejecutan en el servicio antes de guardar. `schema.sql` crea un índice único del DNI después de inicializar JPA, porque SQLite no admite añadir esta restricción mediante el `ALTER TABLE` que usa Hibernate. Una base antigua que ya contenga DNI duplicados requiere corregirlos antes de arrancar; no se eliminan datos automáticamente.

Para probar otra base sin cambiar la configuración, crea previamente su carpeta y ejecuta el JAR con `--spring.datasource.url=jdbc:sqlite:C:/ruta/persona.db`.

## Comprobar el proyecto

```powershell
. .\scripts\java-env.ps1
Initialize-ProjectJava
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Las 27 pruebas cubren CRUD con SQLite real, DNI duplicado tanto en servicio como en base de datos, límites y formatos, campos opcionales, búsqueda, paginación, persistencia entre dos arranques y carga del FXML. También prueban selección de filas, edición, limpieza del formulario, confirmación/cancelación del borrado, retorno a la página anterior al eliminar su último registro y avisos de error.

Las pruebas usan bases temporales, nunca la base del usuario. Las pruebas JavaFX necesitan una sesión gráfica (en Linux sin pantalla, usa una pantalla virtual como Xvfb). Los informes quedan en `target/surefire-reports/` y la captura de revisión de la vista en `target/vista-personas.png`.

## Generar el instalador

En Windows, con **WiX Toolset 3.11** disponible mediante `PATH`, `-WixHome` o `.tools/wix311`:

```powershell
.\scripts\package.ps1 -Type exe
```

El script ejecuta compilación y pruebas, prepara una carpeta que contiene solamente el JAR y genera `instalador/GestionPersonas-1.0.0.exe`, con Java incluido, accesos directos y elección de carpeta. No incluye registros de personas. Usa el icono predeterminado de jpackage. No ejecuta el instalador ni instala el programa durante la compilación.

WiX también se puede extraer desde el [archivo de binarios oficial de WiX 3.11.2](https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm) y pasar su carpeta:

```powershell
.\scripts\package.ps1 -Type exe -WixHome 'C:\herramientas\wix311'
```

Para generar la aplicación portable sin WiX o un MSI:

```powershell
.\scripts\package.ps1 -Type app-image
.\scripts\package.ps1 -Type msi
```

La aplicación portable se abre desde `instalador/GestionPersonas/GestionPersonas.exe`. Debes conservar toda su carpeta, que contiene su entorno Java. `-SkipBuild` reutiliza el JAR ya comprobado. El script no sobrescribe paquetes anteriores: muévelos si deseas regenerarlos.

El JAR ejecutable queda en `target/crud-persona-spring-1.0.0.jar` y se abre con `java -jar`. Su manifiesto declara `JarLauncher` de Spring Boot y `Start-Class: com.example.crudpersona.Launcher`; jpackage utiliza el manifiesto.

Los paquetes para macOS/Linux se generan desde esos sistemas, con `jpackage` y un JAR compilado allí para incluir las bibliotecas JavaFX de la plataforma. Consulta la [documentación oficial de jpackage para Java 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

## Estructura y alcance del manual

| Capa | Archivos |
| --- | --- |
| Arranque e integración | `Launcher`, `JavaFxApplication`, `CrudPersonaApplication` |
| Modelo y validación | `model/Persona` |
| Persistencia | `repository/PersonaRepository`, `application.properties`, `schema.sql` |
| Negocio y transacciones | `service/PersonaService`, `exception/NegocioException` |
| Controlador | `controller/PersonaController` |
| Vista y tema | `view/persona-view.fxml`, `css/app.css`, AtlantaFX PrimerLight |
| Ejecución y distribución | `scripts/run.ps1`, `scripts/package.ps1` |

El sistema principal de los capítulos 1–16 queda implementado. El apartado 13.3 explica `fx:include` para fragmentos que se repiten; esta aplicación tiene una sola ventana y no necesita fragmentos adicionales. Las propuestas del capítulo 17 (DTO/MapStruct, auditoría, Flyway, exportación, consultas en segundo plano y PostgreSQL) son ampliaciones opcionales y quedan fuera de esta versión. Sí se incorporaron las pruebas automatizadas para verificar el funcionamiento solicitado.
