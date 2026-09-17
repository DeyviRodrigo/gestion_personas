package com.example.crudpersona.controller;

import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.service.PersonaService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonaController {

    private final PersonaService service;

    @FXML private TextField txtId;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtDni;
    @FXML private TextField txtEmail;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Persona> tabla;
    @FXML private TableColumn<Persona, String> colId;
    @FXML private TableColumn<Persona, String> colNombre;
    @FXML private TableColumn<Persona, String> colApellido;
    @FXML private TableColumn<Persona, String> colDni;
    @FXML private TableColumn<Persona, String> colEmail;
    @FXML private TableColumn<Persona, String> colTelefono;

    @FXML private Label lblEstado;
    @FXML private Label lblPagina;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnEliminar;

    private final ObservableList<Persona> datos = FXCollections.observableArrayList();
    private int paginaActual = 0;
    private int totalPaginas = 1;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombre()));
        colApellido.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getApellido()));
        colDni.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDni()));
        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(textoODefecto(c.getValue().getEmail())));
        colTelefono.setCellValueFactory(c ->
                new SimpleStringProperty(textoODefecto(c.getValue().getTelefono())));

        tabla.setItems(datos);
        tabla.setPlaceholder(new Label("No hay personas para mostrar."));
        btnEliminar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        tabla.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, actual) -> mostrarEnFormulario(actual));

        txtBuscar.textProperty().addListener((obs, a, b) -> {
            cargarTabla(0);
        });

        cargarTabla(0);
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        txtNombre.requestFocus();
        lblEstado.setText("Formulario limpio: listo para registrar.");
    }

    @FXML
    private void onGuardar() {
        Persona persona = Persona.builder()
                .id(txtId.getText().isBlank() ? null : Long.valueOf(txtId.getText()))
                .nombre(txtNombre.getText().trim())
                .apellido(txtApellido.getText().trim())
                .dni(txtDni.getText().trim())
                .email(txtEmail.getText().trim())
                .telefono(txtTelefono.getText().trim())
                .build();

        try {
            boolean esNuevo = persona.getId() == null;
            Persona guardada = service.guardar(persona);
            String mensaje = esNuevo
                    ? "Registrado correctamente (id " + guardada.getId() + ")."
                    : "Actualizado correctamente (id " + guardada.getId() + ").";
            limpiarFormulario();
            if (cargarTabla(paginaActual)) {
                lblEstado.setText(mensaje);
            }
        } catch (ConstraintViolationException e) {
            String detalle = e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .distinct().sorted()
                    .collect(Collectors.joining("\n"));
            alerta(Alert.AlertType.WARNING, "Revisa los datos", detalle);
        } catch (NegocioException e) {
            alerta(Alert.AlertType.WARNING, "No se pudo guardar", e.getMessage());
        } catch (Exception e) {
            errorDeDatos("guardar", e);
        }
    }

    @FXML
    private void onEliminar() {
        Persona seleccionada = tabla.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            alerta(Alert.AlertType.INFORMATION, "Sin seleccion",
                    "Selecciona una fila de la tabla para eliminar.");
            return;
        }

        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se eliminara a " + seleccionada.getNombreCompleto() + ".",
                ButtonType.CANCEL, ButtonType.OK);
        confirmar.setTitle("Confirmar eliminacion");
        confirmar.setHeaderText("Eliminar registro");
        if (tabla.getScene() != null) {
            confirmar.initOwner(tabla.getScene().getWindow());
        }

        Optional<ButtonType> respuesta = confirmar.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == ButtonType.OK) {
            try {
                service.eliminar(seleccionada.getId());
                limpiarFormulario();
                if (cargarTabla(paginaActual)) {
                    lblEstado.setText("Registro eliminado.");
                }
            } catch (NegocioException e) {
                alerta(Alert.AlertType.WARNING, "No se pudo eliminar", e.getMessage());
            } catch (Exception e) {
                errorDeDatos("eliminar", e);
            }
        }
    }

    @FXML
    private void onAnterior() {
        if (paginaActual > 0) {
            cargarTabla(paginaActual - 1);
        }
    }

    @FXML
    private void onSiguiente() {
        if (paginaActual < totalPaginas - 1) {
            cargarTabla(paginaActual + 1);
        }
    }

    private boolean cargarTabla(int numeroPagina) {
        try {
            Page<Persona> pagina = service.listar(txtBuscar.getText(), numeroPagina);
            int ultimaPagina = Math.max(pagina.getTotalPages() - 1, 0);
            if (numeroPagina > ultimaPagina) {
                pagina = service.listar(txtBuscar.getText(), ultimaPagina);
            }
            paginaActual = pagina.getNumber();
            totalPaginas = Math.max(pagina.getTotalPages(), 1);
            tabla.getSelectionModel().clearSelection();
            limpiarFormulario();
            datos.setAll(pagina.getContent());
            lblPagina.setText("Página " + (paginaActual + 1) + " de " + totalPaginas);
            btnAnterior.setDisable(paginaActual == 0);
            btnSiguiente.setDisable(paginaActual >= totalPaginas - 1);
            lblEstado.setText(pagina.getTotalElements() + " persona(s) en total.");
            return true;
        } catch (Exception e) {
            errorDeDatos("cargar los registros", e);
            return false;
        }
    }

    private void mostrarEnFormulario(Persona p) {
        if (p == null) {
            limpiarFormulario();
            return;
        }
        txtId.setText(String.valueOf(p.getId()));
        txtNombre.setText(p.getNombre());
        txtApellido.setText(p.getApellido());
        txtDni.setText(p.getDni());
        txtEmail.setText(textoODefecto(p.getEmail()));
        txtTelefono.setText(textoODefecto(p.getTelefono()));
    }

    private void limpiarFormulario() {
        txtId.clear();
        txtNombre.clear();
        txtApellido.clear();
        txtDni.clear();
        txtEmail.clear();
        txtTelefono.clear();
    }

    private String textoODefecto(String valor) {
        return valor == null ? "" : valor;
    }

    private void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo, mensaje, ButtonType.OK);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        a.getDialogPane().setMinWidth(420);
        if (tabla.getScene() != null) {
            a.initOwner(tabla.getScene().getWindow());
        }
        a.showAndWait();
    }

    private void errorDeDatos(String operacion, Exception e) {
        log.error("No se pudo {}", operacion, e);
        lblEstado.setText("No se pudo " + operacion + ".");
        alerta(Alert.AlertType.ERROR, "Error de base de datos",
                "No se pudo " + operacion + ". Revisa el acceso a la base de datos e inténtalo de nuevo.");
    }
}
