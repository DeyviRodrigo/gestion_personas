package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonaExportServiceTest {
    @TempDir Path directorio;

    @Test void excelConservaCerosYNoInterpretaFormulas() throws Exception {
        PersonaService personas = mock(PersonaService.class);
        when(personas.listarParaExportar("Ana")).thenReturn(List.of(persona(1, "=1+1"), persona(2, "José")));
        Path path = directorio.resolve("personas.xlsx");
        var resultado = new PersonaExportService(personas).exportar(path, PersonaExportService.Formato.EXCEL, "Ana");
        assertThat(resultado.registros()).isEqualTo(2);
        try (var entrada = Files.newInputStream(path); var libro = new XSSFWorkbook(entrada)) {
            var hoja = libro.getSheet("Personas");
            assertThat(hoja.getLastRowNum()).isEqualTo(2);
            assertThat(hoja.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("=1+1");
            assertThat(hoja.getRow(1).getCell(3).getStringCellValue()).isEqualTo("01234567");
            assertThat(hoja.getRow(1).getCell(8).getStringCellValue()).isEqualTo("pruebas");
        }
    }

    @Test void pdfMultipaginaIncluyeTodosLosRegistrosYAcentos() throws Exception {
        PersonaService personas = mock(PersonaService.class);
        when(personas.listarParaExportar("")).thenReturn(IntStream.rangeClosed(1, 55).mapToObj(i -> persona(i, "José María " + i)).toList());
        Path path = Path.of("target", "personas-exportacion-prueba.pdf");
        new PersonaExportService(personas).exportar(path, PersonaExportService.Formato.PDF, "");
        try (PdfReader reader = new PdfReader(Files.readAllBytes(path))) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder contenido = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) { contenido.append(extractor.getTextFromPage(i)); }
            assertThat(contenido.toString()).contains("Gestión de Personas", "José María 55", "01234567", "55 persona(s)");
        }
    }

    @Test void exportaListaVaciaYConservaArchivoPrevioSiFallaLaConsulta() throws Exception {
        PersonaService personas = mock(PersonaService.class);
        when(personas.listarParaExportar("")).thenReturn(List.of());
        var exportador = new PersonaExportService(personas);
        Path path = directorio.resolve("vacio.pdf");
        assertThat(exportador.exportar(path, PersonaExportService.Formato.PDF, "").registros()).isZero();
        byte[] antes = Files.readAllBytes(path);
        when(personas.listarParaExportar("fallo")).thenThrow(new IllegalStateException("sin conexión"));
        assertThatThrownBy(() -> exportador.exportar(path, PersonaExportService.Formato.PDF, "fallo")).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readAllBytes(path)).isEqualTo(antes);
    }

    private PersonaDto persona(long id, String nombre) {
        return PersonaDto.builder().id(id).nombre(nombre).apellido("Pérez").dni("01234567").email("ana@example.com")
                .telefono("987654321").creadoEn(Instant.parse("2026-01-01T00:00:00Z"))
                .actualizadoEn(Instant.parse("2026-01-02T00:00:00Z")).creadoPor("pruebas").actualizadoPor("pruebas").build();
    }
}
