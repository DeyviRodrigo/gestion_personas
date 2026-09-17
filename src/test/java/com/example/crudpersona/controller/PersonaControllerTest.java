package com.example.crudpersona.controller;

import atlantafx.base.theme.PrimerLight;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.service.PersonaService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonaControllerTest {
    PersonaService service;
    FXMLLoader loader;
    Parent root;

    @BeforeAll
    static void iniciarJavaFx() throws Exception {
        CountDownLatch listo = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            listo.countDown();
        });
        assertThat(listo.await(15, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    static void cerrarJavaFx() {
        Platform.exit();
    }

    @BeforeEach
    void preparar() throws Exception {
        service = mock(PersonaService.class);
        when(service.listar(anyString(), anyInt())).thenReturn(pagina(List.of(persona(1)), 0, 1));
        fx(() -> {
            loader = new FXMLLoader(getClass().getResource("/view/persona-view.fxml"));
            loader.setControllerFactory(tipo -> new PersonaController(service));
            root = loader.load();
            Scene scene = new Scene(root, 980, 660);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            root.applyCss();
            root.layout();
        });
    }

    @Test
    void cargaFxmlColumnasSeleccionYNuevo() throws Exception {
        fx(() -> {
            TableView<Persona> tabla = tabla();
            assertThat(tabla.getItems()).hasSize(1);
            assertThat(tabla.getColumns().get(1).getCellData(0)).isEqualTo("Ana");
            assertThat(boton("btnEliminar").isDisabled()).isTrue();
            tabla.getSelectionModel().selectFirst();
            assertThat(campo("txtId").getText()).isEqualTo("1");
            assertThat(campo("txtNombre").getText()).isEqualTo("Ana");
            assertThat(boton("btnEliminar").isDisabled()).isFalse();
            boton("btnNuevo").fire();
            assertThat(campo("txtId").getText()).isEmpty();
            assertThat(campo("txtNombre").getText()).isEmpty();
            assertThat(tabla.getSelectionModel().getSelectedItem()).isNull();
        });
    }

    @Test
    void guardarLimpiaSeleccionYConservaMensajeDeExito() throws Exception {
        when(service.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            campo("txtNombre").setText(" Andrea ");
            boton("btnGuardar").fire();
            verify(service).guardar(argThat(p -> p.getNombre().equals("Andrea") && p.getId() == 1));
            assertThat(etiqueta("lblEstado").getText()).contains("Actualizado correctamente");
            assertThat(campo("txtId").getText()).isEmpty();
            assertThat(boton("btnEliminar").isDisabled()).isTrue();
        });
    }

    @Test
    void filtrarVuelveALaPrimeraPaginaYLimpiaRegistroAnterior() throws Exception {
        when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 16));
        when(service.listar("", 1)).thenReturn(pagina(List.of(persona(16)), 1, 16));
        fx(() -> {
            campo("txtBuscar").setText("a");
            campo("txtBuscar").clear();
            boton("btnSiguiente").fire();
            tabla().getSelectionModel().selectFirst();
            assertThat(campo("txtId").getText()).isEqualTo("16");
            campo("txtBuscar").setText("Ana");
            verify(service).listar("Ana", 0);
            assertThat(etiqueta("lblPagina").getText()).isEqualTo("Página 1 de 1");
            assertThat(campo("txtId").getText()).isEmpty();
        });
    }

    @Test
    void eliminarUltimoRegistroVuelveALaPaginaAnterior() throws Exception {
        when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 16));
        when(service.listar("", 1)).thenReturn(pagina(List.of(persona(16)), 1, 16))
                .thenReturn(pagina(List.of(), 1, 15));
        fx(() -> {
            campo("txtBuscar").setText("a");
            campo("txtBuscar").clear();
            boton("btnSiguiente").fire();
            tabla().getSelectionModel().selectFirst();
            when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 15));
            responderDialogo(ButtonType.OK);
            boton("btnEliminar").fire();
            verify(service).eliminar(16L);
            assertThat(etiqueta("lblPagina").getText()).isEqualTo("Página 1 de 1");
            assertThat(boton("btnSiguiente").isDisabled()).isTrue();
            assertThat(boton("btnAnterior").isDisabled()).isTrue();
            assertThat(etiqueta("lblEstado").getText()).isEqualTo("Registro eliminado.");
        });
    }

    @Test
    void cancelarEliminacionConservaElRegistro() throws Exception {
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            responderDialogo(ButtonType.CANCEL);
            boton("btnEliminar").fire();
            verify(service, never()).eliminar(anyLong());
            assertThat(tabla().getItems()).hasSize(1);
        });
    }

    @Test
    void errorDeBaseDeDatosSeMuestraSinPerderElFormulario() throws Exception {
        doThrow(new IllegalStateException("Fallo simulado")).when(service).guardar(any());
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            responderDialogo(ButtonType.OK);
            boton("btnGuardar").fire();
            assertThat(etiqueta("lblEstado").getText()).contains("No se pudo guardar");
            assertThat(campo("txtNombre").getText()).isEqualTo("Ana");
        });
    }

    @Test
    void generaVistaParaRevisionVisual() throws Exception {
        fx(() -> {
            var imagen = root.getScene().snapshot(null);
            BufferedImage png = new BufferedImage((int) imagen.getWidth(), (int) imagen.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) {
                for (int x = 0; x < png.getWidth(); x++) {
                    png.setRGB(x, y, imagen.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(png, "png", Path.of("target", "vista-personas.png").toFile());
        });
    }

    private void responderDialogo(ButtonType respuesta) {
        Platform.runLater(() -> List.copyOf(Window.getWindows()).stream()
                .filter(w -> w.getScene().getRoot() instanceof DialogPane)
                .map(w -> (DialogPane) w.getScene().getRoot())
                .forEach(p -> ((Button) p.lookupButton(respuesta)).fire()));
    }

    @SuppressWarnings("unchecked")
    private TableView<Persona> tabla() { return (TableView<Persona>) loader.getNamespace().get("tabla"); }
    private TextField campo(String id) { return (TextField) loader.getNamespace().get(id); }
    private Button boton(String id) { return (Button) loader.getNamespace().get(id); }
    private Label etiqueta(String id) { return (Label) loader.getNamespace().get(id); }

    private static Persona persona(long id) {
        return Persona.builder().id(id).nombre("Ana").apellido("Torres").dni("12345678")
                .email("ana@example.com").telefono("987654321").build();
    }

    private static PageImpl<Persona> pagina(List<Persona> personas, int pagina, long total) {
        return new PageImpl<>(personas, PageRequest.of(pagina, 15), total);
    }

    private static void fx(Operacion operacion) throws Exception {
        FutureTask<Void> tarea = new FutureTask<>(() -> { operacion.ejecutar(); return null; });
        Platform.runLater(tarea);
        tarea.get(20, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface Operacion { void ejecutar() throws Exception; }
}
