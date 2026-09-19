package com.example.crudpersona.controller;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.service.PersonaExportService;
import com.example.crudpersona.service.PersonaService;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PersonaController {
    private final PersonaService service;
    private final PersonaExportService exportService;
    private final Executor executor;
    private final ObservableList<PersonaDto> datos = FXCollections.observableArrayList();
    private final BooleanProperty cargando = new SimpleBooleanProperty();
    private final BooleanProperty guardando = new SimpleBooleanProperty();
    private final BooleanProperty exportando = new SimpleBooleanProperty();
    private final PauseTransition busqueda = new PauseTransition(Duration.millis(300));
    private Task<?> consulta;
    private long generacion;
    private boolean cerrado;
    private int paginaActual;
    private int totalPaginas = 1;
    private PersonaDto seleccionada;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private TextField txtId, txtNombre, txtApellido, txtDni, txtEmail, txtTelefono, txtBuscar;
    @FXML private GridPane formulario;
    @FXML private TableView<PersonaDto> tabla;
    @FXML private TableColumn<PersonaDto, String> colId, colNombre, colApellido, colDni, colEmail, colTelefono;
    @FXML private Label lblEstado, lblPagina, lblAuditoria;
    @FXML private Button btnAnterior, btnSiguiente, btnEliminar, btnGuardar, btnNuevo, btnActualizar, btnExcel, btnPdf;
    @FXML private ProgressIndicator progreso;

    public PersonaController(PersonaService service, PersonaExportService exportService,
                             @Qualifier("personaExecutor") Executor executor) {
        this.service = service;
        this.exportService = exportService;
        this.executor = executor;
    }

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colApellido.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getApellido()));
        colDni.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDni()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getEmail())));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getTelefono())));
        // La consulta mantiene el orden global por ID; ordenar solo la página sería engañoso.
        tabla.getColumns().forEach(c -> c.setSortable(false));
        tabla.setItems(datos);
        tabla.setPlaceholder(new Label("No hay personas para mostrar."));
        var ocupado = cargando.or(guardando).or(exportando);
        btnGuardar.disableProperty().bind(ocupado);
        btnNuevo.disableProperty().bind(cargando.or(guardando));
        btnEliminar.disableProperty().bind(ocupado.or(tabla.getSelectionModel().selectedItemProperty().isNull()));
        btnActualizar.disableProperty().bind(ocupado);
        btnExcel.disableProperty().bind(ocupado);
        btnPdf.disableProperty().bind(ocupado);
        tabla.disableProperty().bind(cargando.or(guardando));
        formulario.disableProperty().bind(cargando.or(guardando));
        txtBuscar.disableProperty().bind(guardando.or(exportando));
        progreso.visibleProperty().bind(ocupado);
        progreso.managedProperty().bind(progreso.visibleProperty());
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> mostrarEnFormulario(ahora));
        busqueda.setOnFinished(e -> cargarTabla(0, null));
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> {
            invalidarConsulta();
            cargando.set(true);
            actualizarPaginacion();
            lblEstado.setText("Buscando personas…");
            busqueda.playFromStart();
        });
        cargarTabla(0, null);
    }

    @FXML private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        txtNombre.requestFocus();
        lblEstado.setText("Formulario limpio: listo para registrar.");
    }

    @FXML private void onGuardar() {
        PersonaDto dto = PersonaDto.builder().id(seleccionada == null ? null : seleccionada.getId())
                .version(seleccionada == null ? null : seleccionada.getVersion())
                .nombre(txtNombre.getText().trim()).apellido(txtApellido.getText().trim())
                .dni(txtDni.getText().trim()).email(txtEmail.getText().trim()).telefono(txtTelefono.getText().trim()).build();
        guardando.set(true);
        actualizarPaginacion();
        lblEstado.setText("Guardando persona…");
        ejecutar(() -> service.guardar(dto), resultado -> {
            guardando.set(false);
            limpiarFormulario();
            cargarTabla(paginaActual, (dto.getId() == null ? "Registrado" : "Actualizado")
                    + " correctamente (id " + resultado.getId() + ").");
        }, error -> { guardando.set(false); actualizarPaginacion(); mostrarError("guardar", error); });
    }

    @FXML private void onEliminar() {
        PersonaDto persona = tabla.getSelectionModel().getSelectedItem();
        if (persona == null) { return; }
        Alert confirmar = crearAlerta(Alert.AlertType.CONFIRMATION, "Confirmar eliminación",
                "Se eliminará a " + persona.getNombreCompleto() + ".", ButtonType.CANCEL, ButtonType.OK);
        if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) { return; }
        guardando.set(true);
        actualizarPaginacion();
        lblEstado.setText("Eliminando registro…");
        ejecutar(() -> { service.eliminar(persona.getId(), persona.getVersion()); return true; }, resultado -> {
            guardando.set(false);
            cargarTabla(paginaActual, "Registro eliminado.");
        }, error -> { guardando.set(false); actualizarPaginacion(); mostrarError("eliminar", error); });
    }

    @FXML private void onAnterior() { if (paginaActual > 0) { cargarTabla(paginaActual - 1, null); } }
    @FXML private void onSiguiente() { if (paginaActual < totalPaginas - 1) { cargarTabla(paginaActual + 1, null); } }
    @FXML private void onActualizar() { cargarTabla(paginaActual, null); }
    @FXML private void onExportarExcel() { exportar(PersonaExportService.Formato.EXCEL); }
    @FXML private void onExportarPdf() { exportar(PersonaExportService.Formato.PDF); }

    private void cargarTabla(int numeroPagina, String mensaje) {
        busqueda.stop();
        invalidarConsulta();
        long solicitud = generacion;
        String filtro = txtBuscar.getText();
        cargando.set(true);
        actualizarPaginacion();
        lblEstado.setText("Consultando personas…");
        consulta = ejecutar(() -> {
            Page<PersonaDto> pagina = service.listar(filtro, numeroPagina);
            int ultima = Math.max(pagina.getTotalPages() - 1, 0);
            return numeroPagina > ultima ? service.listar(filtro, ultima) : pagina;
        }, pagina -> {
            if (solicitud != generacion) { return; }
            paginaActual = pagina.getNumber();
            totalPaginas = Math.max(pagina.getTotalPages(), 1);
            tabla.getSelectionModel().clearSelection();
            limpiarFormulario();
            datos.setAll(pagina.getContent());
            cargando.set(false);
            actualizarPaginacion();
            lblEstado.setText(mensaje == null ? pagina.getTotalElements() + " persona(s) en total." : mensaje);
        }, error -> {
            if (solicitud != generacion) { return; }
            cargando.set(false);
            datos.clear();
            limpiarFormulario();
            paginaActual = 0;
            totalPaginas = 1;
            actualizarPaginacion();
            mostrarError("consultar los registros", error);
        });
    }

    private void invalidarConsulta() {
        generacion++;
        if (consulta != null) { consulta.cancel(true); }
    }

    private void actualizarPaginacion() {
        lblPagina.setText("Página " + (paginaActual + 1) + " de " + totalPaginas);
        boolean ocupado = cargando.get() || guardando.get();
        btnAnterior.setDisable(ocupado || paginaActual == 0);
        btnSiguiente.setDisable(ocupado || paginaActual >= totalPaginas - 1);
    }

    private void exportar(PersonaExportService.Formato formato) {
        String extension = formato == PersonaExportService.Formato.EXCEL ? "xlsx" : "pdf";
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar personas" + (txtBuscar.getText().isBlank() ? "" : " filtradas"));
        selector.setInitialFileName("personas." + extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.toUpperCase(), "*." + extension));
        File archivo = selector.showSaveDialog(tabla.getScene().getWindow());
        if (archivo == null) { return; }
        String filtro = txtBuscar.getText();
        exportando.set(true);
        lblEstado.setText("Exportando todos los resultados del filtro…");
        ejecutar(() -> exportService.exportar(archivo.toPath(), formato, filtro), resultado -> {
            exportando.set(false);
            lblEstado.setText("Exportados " + resultado.registros() + " registros a " + resultado.archivo().getFileName());
        }, error -> { exportando.set(false); mostrarError("exportar", error); });
    }

    private <T> Task<T> ejecutar(Callable<T> trabajo, Consumer<T> exito, Consumer<Throwable> error) {
        Task<T> tarea = new Task<>() { @Override protected T call() throws Exception { return trabajo.call(); } };
        tarea.setOnSucceeded(e -> { if (!cerrado) { exito.accept(tarea.getValue()); } });
        tarea.setOnFailed(e -> { if (!cerrado) { error.accept(tarea.getException()); } });
        try {
            executor.execute(tarea);
        } catch (RejectedExecutionException saturado) {
            tarea.cancel();
            javafx.application.Platform.runLater(() -> { if (!cerrado) { error.accept(saturado); } });
        }
        return tarea;
    }

    private void mostrarEnFormulario(PersonaDto persona) {
        if (persona == null) { limpiarFormulario(); return; }
        seleccionada = persona;
        txtId.setText(String.valueOf(persona.getId()));
        txtNombre.setText(persona.getNombre());
        txtApellido.setText(persona.getApellido());
        txtDni.setText(persona.getDni());
        txtEmail.setText(texto(persona.getEmail()));
        txtTelefono.setText(texto(persona.getTelefono()));
        lblAuditoria.setText(persona.getCreadoEn() == null ? "" : "Creado: " + FECHA.format(persona.getCreadoEn())
                + " por " + persona.getCreadoPor() + " · Modificado: " + FECHA.format(persona.getActualizadoEn())
                + " por " + persona.getActualizadoPor());
    }

    private void limpiarFormulario() {
        seleccionada = null;
        txtId.clear(); txtNombre.clear(); txtApellido.clear(); txtDni.clear(); txtEmail.clear(); txtTelefono.clear();
        lblAuditoria.setText("Selecciona un registro para consultar su auditoría.");
    }

    private String texto(String valor) { return valor == null ? "" : valor; }

    private Alert crearAlerta(Alert.AlertType tipo, String titulo, String mensaje, ButtonType... botones) {
        Alert alerta = new Alert(tipo, mensaje, botones);
        alerta.setTitle(titulo);
        alerta.setHeaderText(titulo);
        alerta.getDialogPane().setMinWidth(420);
        if (tabla.getScene() != null) { alerta.initOwner(tabla.getScene().getWindow()); }
        return alerta;
    }

    private void mostrarError(String operacion, Throwable error) {
        String mensaje;
        if (error instanceof ConstraintViolationException validacion) {
            mensaje = validacion.getConstraintViolations().stream().map(ConstraintViolation::getMessage)
                    .distinct().sorted().collect(Collectors.joining("\n"));
        } else if (error instanceof NegocioException) {
            mensaje = error.getMessage();
        } else if (error instanceof OptimisticLockingFailureException) {
            mensaje = "Otra sesión cambió este registro. Actualiza la tabla antes de continuar.";
        } else if (error instanceof RejectedExecutionException) {
            mensaje = "Hay varias operaciones pendientes. Espera un momento y vuelve a intentarlo.";
        } else {
            log.error("No se pudo {}", operacion, error);
            mensaje = "No se pudo " + operacion + ". Comprueba la conexión a la base de datos y los permisos de la carpeta de destino.";
        }
        lblEstado.setText("No se pudo " + operacion + ".");
        crearAlerta(Alert.AlertType.WARNING, "Revisa la operación", mensaje, ButtonType.OK).showAndWait();
    }

    @PreDestroy
    public void cerrar() {
        cerrado = true;
        // JavaFxApplication llama este método desde el hilo FX antes de cerrar Spring.
        if (javafx.application.Platform.isFxApplicationThread()) { busqueda.stop(); invalidarConsulta(); }
    }
}
