package com.example.crudpersona;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext context;
    private RuntimeException errorInicio;

    @Override
    public void init() {
        try {
            this.context = new SpringApplicationBuilder(CrudPersonaApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (RuntimeException error) {
            errorInicio = error;
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        if (errorInicio != null) {
            Alert aviso = new Alert(Alert.AlertType.ERROR);
            aviso.setTitle("Gestión de Personas");
            aviso.setHeaderText("No se pudo iniciar la aplicación");
            aviso.setContentText("Comprueba que la base PostgreSQL esté disponible y revisa la configuración en "
                    + System.getProperty("user.home") + "/.gestion-personas/database.properties. "
                    + "Consulta el README del proyecto para configurar la conexión y las migraciones.");
            aviso.showAndWait();
            Platform.exit();
            return;
        }
        // Tema visual: hay PrimerDark, NordLight, NordDark, Dracula, etc.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/view/persona-view.fxml"), "No se encontró la vista de personas"));

        // LINEA CLAVE: Spring entrega los controladores ya inyectados
        loader.setControllerFactory(context::getBean);

        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        stage.setTitle("Gestión de Personas | Spring Boot + JavaFX");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(700);
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.getBean(com.example.crudpersona.controller.PersonaController.class).cerrar();
            context.close();
        }
        Platform.exit();
    }
}
