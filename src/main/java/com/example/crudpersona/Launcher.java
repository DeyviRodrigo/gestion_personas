package com.example.crudpersona;

import javafx.application.Application;
import java.nio.file.Files;
import java.nio.file.Path;

public class Launcher {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(System.getProperty("user.home"), ".gestion-personas"));
        Application.launch(JavaFxApplication.class, args);
    }
}
