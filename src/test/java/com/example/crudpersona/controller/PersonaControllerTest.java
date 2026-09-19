package com.example.crudpersona.controller;

import atlantafx.base.theme.PrimerLight;
import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.service.PersonaExportService;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonaControllerTest {
    PersonaService service;
    PersonaExportService exportService;
    ExecutorService executor;
    FXMLLoader loader;
    Parent root;

    @BeforeAll static void iniciarJavaFx() throws Exception {
        CountDownLatch listo = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            listo.countDown();
        });
        assertThat(listo.await(15, TimeUnit.SECONDS)).isTrue();
    }
    @AfterAll static void cerrarJavaFx() { Platform.exit(); }

    @BeforeEach void preparar() throws Exception {
        service = mock(PersonaService.class);
        exportService = mock(PersonaExportService.class);
        executor = Executors.newFixedThreadPool(3);
        when(service.listar(anyString(), anyInt())).thenAnswer(i -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            return pagina(List.of(persona(1)), 0, 1);
        });
        fx(() -> {
            loader = new FXMLLoader(getClass().getResource("/view/persona-view.fxml"));
            loader.setControllerFactory(tipo -> new PersonaController(service, exportService, executor));
            root = loader.load();
            Scene scene = new Scene(root, 1060, 750);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            root.applyCss(); root.layout();
        });
        esperar(() -> !boton("btnGuardar").isDisabled());
    }

    @AfterEach void terminar() throws Exception {
        fx(() -> ((PersonaController) loader.getController()).cerrar());
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void cargaFxmlColumnasSeleccionAuditoriaYNuevo() throws Exception {
        fx(() -> {
            assertThat(tabla().getItems()).hasSize(1);
            assertThat(tabla().getColumns().get(1).getCellData(0)).isEqualTo("Ana");
            assertThat(boton("btnEliminar").isDisabled()).isTrue();
            tabla().getSelectionModel().selectFirst();
            assertThat(campo("txtId").getText()).isEqualTo("1");
            assertThat(etiqueta("lblAuditoria").getText()).contains("Creado:", "pruebas", "Modificado:");
            boton("btnNuevo").fire();
            assertThat(campo("txtId").getText()).isEmpty();
            assertThat(tabla().getSelectionModel().getSelectedItem()).isNull();
        });
    }

    @Test void guardaFueraDelHiloGraficoYLimpiaSeleccion() throws Exception {
        when(service.guardar(any())).thenAnswer(i -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            return i.getArgument(0);
        });
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            campo("txtNombre").setText(" Andrea ");
            boton("btnGuardar").fire();
        });
        esperar(() -> etiqueta("lblEstado").getText().contains("Actualizado correctamente"));
        verify(service).guardar(argThat(p -> p.getNombre().equals("Andrea") && p.getId() == 1 && p.getVersion() == 0));
        fx(() -> {
            assertThat(campo("txtId").getText()).isEmpty();
            assertThat(boton("btnEliminar").isDisabled()).isTrue();
        });
    }

    @Test void filtrarVuelveALaPrimeraPagina() throws Exception {
        when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 16));
        when(service.listar("", 1)).thenReturn(pagina(List.of(persona(16)), 1, 16));
        fx(() -> boton("btnActualizar").fire());
        esperar(() -> !boton("btnSiguiente").isDisabled());
        fx(() -> boton("btnSiguiente").fire());
        esperar(() -> tabla().getItems().get(0).getId() == 16);
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            campo("txtBuscar").setText("Ana");
        });
        esperar(() -> !boton("btnGuardar").isDisabled() && etiqueta("lblPagina").getText().equals("Página 1 de 1"));
        verify(service).listar("Ana", 0);
        fx(() -> assertThat(campo("txtId").getText()).isEmpty());
    }

    @Test void eliminarUltimoRegistroVuelveALaPaginaAnterior() throws Exception {
        when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 16));
        when(service.listar("", 1)).thenReturn(pagina(List.of(persona(16)), 1, 16))
                .thenReturn(pagina(List.of(), 1, 15));
        fx(() -> boton("btnActualizar").fire());
        esperar(() -> !boton("btnSiguiente").isDisabled());
        fx(() -> boton("btnSiguiente").fire());
        esperar(() -> tabla().getItems().get(0).getId() == 16);
        fx(() -> tabla().getSelectionModel().selectFirst());
        when(service.listar("", 0)).thenReturn(pagina(List.of(persona(1)), 0, 15));
        Platform.runLater(() -> boton("btnEliminar").fire());
        responderDialogo(ButtonType.OK);
        esperar(() -> etiqueta("lblEstado").getText().equals("Registro eliminado."));
        verify(service).eliminar(16L, 0L);
        fx(() -> {
            assertThat(etiqueta("lblPagina").getText()).isEqualTo("Página 1 de 1");
            assertThat(boton("btnSiguiente").isDisabled()).isTrue();
        });
    }

    @Test void cancelarEliminacionConservaRegistro() throws Exception {
        fx(() -> tabla().getSelectionModel().selectFirst());
        Platform.runLater(() -> boton("btnEliminar").fire());
        responderDialogo(ButtonType.CANCEL);
        verify(service, never()).eliminar(anyLong(), anyLong());
        fx(() -> assertThat(tabla().getItems()).hasSize(1));
    }

    @Test void errorAsincronoConservaFormulario() throws Exception {
        doThrow(new IllegalStateException("Fallo simulado")).when(service).guardar(any());
        fx(() -> { tabla().getSelectionModel().selectFirst(); boton("btnGuardar").fire(); });
        responderDialogo(ButtonType.OK);
        fx(() -> {
            assertThat(etiqueta("lblEstado").getText()).contains("No se pudo guardar");
            assertThat(campo("txtNombre").getText()).isEqualTo("Ana");
            assertThat(boton("btnGuardar").isDisabled()).isFalse();
        });
    }

    @Test void busquedaLentaNoBloqueaNiReemplazaUnResultadoMasReciente() throws Exception {
        CountDownLatch iniciada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(service.listar("vieja", 0)).thenAnswer(i -> {
            iniciada.countDown();
            boolean esperando = true;
            while (esperando) {
                try { liberar.await(); esperando = false; } catch (InterruptedException ignored) { }
            }
            return pagina(List.of(persona(99)), 0, 1);
        });
        when(service.listar("nueva", 0)).thenReturn(pagina(List.of(persona(2)), 0, 1));
        try {
            fx(() -> campo("txtBuscar").setText("vieja"));
            assertThat(iniciada.await(5, TimeUnit.SECONDS)).isTrue();
            fx(() -> campo("txtBuscar").setText("nueva"));
            esperar(() -> tabla().getItems().get(0).getId() == 2);
            liberar.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            fx(() -> assertThat(tabla().getItems()).extracting(PersonaDto::getId).containsExactly(2L));
        } finally { liberar.countDown(); }
    }

    @Test void rechazarUnaTareaNoDejaLaInterfazBloqueada() throws Exception {
        executor.shutdown();
        fx(() -> boton("btnActualizar").fire());
        responderDialogo(ButtonType.OK);
        fx(() -> {
            assertThat(boton("btnActualizar").isDisabled()).isFalse();
            assertThat(boton("btnGuardar").isDisabled()).isFalse();
            assertThat(etiqueta("lblEstado").getText()).contains("No se pudo");
        });
    }

    @Test void generaVistaParaRevisionVisual() throws Exception {
        fx(() -> {
            tabla().getSelectionModel().selectFirst();
            root.applyCss(); root.layout();
            var imagen = root.getScene().snapshot(null);
            BufferedImage png = new BufferedImage((int) imagen.getWidth(), (int) imagen.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) {
                for (int x = 0; x < png.getWidth(); x++) { png.setRGB(x, y, imagen.getPixelReader().getArgb(x, y)); }
            }
            ImageIO.write(png, "png", Path.of("target", "vista-personas-v2.png").toFile());
            assertThat(boton("btnExcel").getOnAction()).isNotNull();
            assertThat(boton("btnPdf").getOnAction()).isNotNull();
        });
    }

    private void responderDialogo(ButtonType respuesta) throws Exception {
        esperar(() -> Window.getWindows().stream().anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        fx(() -> List.copyOf(Window.getWindows()).stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
                .map(w -> (DialogPane) w.getScene().getRoot()).forEach(p -> ((Button) p.lookupButton(respuesta)).fire()));
    }
    private void esperar(BooleanSupplier condicion) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < limite) {
            if (enFx(condicion::getAsBoolean)) { return; }
            Thread.sleep(25);
        }
        throw new AssertionError("La interfaz no alcanzó el estado esperado.");
    }
    @SuppressWarnings("unchecked") private TableView<PersonaDto> tabla() { return (TableView<PersonaDto>) loader.getNamespace().get("tabla"); }
    private TextField campo(String id) { return (TextField) loader.getNamespace().get(id); }
    private Button boton(String id) { return (Button) loader.getNamespace().get(id); }
    private Label etiqueta(String id) { return (Label) loader.getNamespace().get(id); }
    private static PersonaDto persona(long id) {
        return PersonaDto.builder().id(id).version(0L).nombre("Ana").apellido("Torres").dni("12345678")
                .email("ana@example.com").telefono("987654321").creadoEn(Instant.parse("2026-09-18T12:00:00Z"))
                .actualizadoEn(Instant.parse("2026-09-18T13:00:00Z")).creadoPor("pruebas").actualizadoPor("pruebas").build();
    }
    private static PageImpl<PersonaDto> pagina(List<PersonaDto> personas, int pagina, long total) { return new PageImpl<>(personas, PageRequest.of(pagina, 15), total); }
    private static <T> T enFx(Callable<T> operacion) throws Exception {
        FutureTask<T> tarea = new FutureTask<>(operacion);
        Platform.runLater(tarea);
        return tarea.get(15, TimeUnit.SECONDS);
    }
    private static void fx(Operacion operacion) throws Exception { enFx(() -> { operacion.ejecutar(); return null; }); }
    @FunctionalInterface private interface Operacion { void ejecutar() throws Exception; }
}
