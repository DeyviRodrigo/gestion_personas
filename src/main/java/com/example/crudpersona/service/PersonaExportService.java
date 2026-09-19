package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonaExportService {
    public enum Formato { EXCEL, PDF }
    public record Resultado(Path archivo, int registros) {}
    private final PersonaService personas;
    private volatile JasperReport informe;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    public Resultado exportar(Path destino, Formato formato, String filtro) throws Exception {
        List<PersonaDto> datos = personas.listarParaExportar(filtro);
        Path absoluto = destino.toAbsolutePath();
        Path temporal = Files.createTempFile(absoluto.getParent(), ".personas-", ".tmp");
        try {
            try (OutputStream salida = Files.newOutputStream(temporal)) {
                if (formato == Formato.EXCEL) { escribirExcel(datos, salida); }
                else { escribirPdf(datos, filtro, salida); }
            }
            try {
                Files.move(temporal, absoluto, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Resultado(absoluto, datos.size());
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private void escribirExcel(List<PersonaDto> datos, OutputStream salida) throws Exception {
        SXSSFWorkbook libro = new SXSSFWorkbook(100);
        try (libro) {
            Sheet hoja = libro.createSheet("Personas");
            String[] cabeceras = {"ID", "Nombre", "Apellido", "DNI", "Correo", "Teléfono", "Creado", "Modificado", "Creado por", "Modificado por"};
            CellStyle cabecera = libro.createCellStyle();
            cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(fuente);
            Row fila = hoja.createRow(0);
            fila.setHeightInPoints(24);
            int[] anchos = {12, 25, 25, 12, 38, 16, 24, 24, 24, 24};
            for (int i = 0; i < cabeceras.length; i++) {
                fila.createCell(i).setCellValue(cabeceras[i]);
                fila.getCell(i).setCellStyle(cabecera);
                hoja.setColumnWidth(i, anchos[i] * 256);
            }
            int numero = 1;
            for (PersonaDto p : datos) {
                Row registro = hoja.createRow(numero++);
                String[] valores = {String.valueOf(p.getId()), p.getNombre(), p.getApellido(), p.getDni(), p.getEmail(), p.getTelefono(),
                        p.getCreadoEn() == null ? "" : FECHA.format(p.getCreadoEn()),
                        p.getActualizadoEn() == null ? "" : FECHA.format(p.getActualizadoEn()), p.getCreadoPor(), p.getActualizadoPor()};
                for (int i = 0; i < valores.length; i++) {
                    // Texto explícito: preserva ceros del DNI y no interpreta fórmulas del usuario.
                    registro.createCell(i, CellType.STRING).setCellValue(valores[i] == null ? "" : valores[i]);
                }
            }
            hoja.createFreezePane(0, 1);
            hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, numero - 1), 0, 9));
            hoja.setRepeatingRows(new CellRangeAddress(0, 0, -1, -1));
            libro.write(salida);
        } finally {
            libro.dispose();
        }
    }

    private void escribirPdf(List<PersonaDto> datos, String filtro, OutputStream salida) throws Exception {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("FILTRO", filtro == null || filtro.isBlank() ? "Todos los registros" : "Filtro: " + filtro);
        parametros.put("RESUMEN", datos.size() + " persona(s) · Generado el " + FECHA.format(java.time.Instant.now()));
        JasperPrint documento = JasperFillManager.fillReport(obtenerInforme(), parametros, new JRBeanCollectionDataSource(datos));
        JasperExportManager.exportReportToPdfStream(documento, salida);
    }

    private JasperReport obtenerInforme() throws JRException {
        if (informe != null) { return informe; }
        synchronized (this) {
            if (informe != null) { return informe; }
            JasperDesign design = new JasperDesign();
            design.setName("personas");
            design.setPageWidth(842);
            design.setPageHeight(595);
            design.setOrientation(OrientationEnum.LANDSCAPE);
            design.setLeftMargin(30);
            design.setRightMargin(30);
            design.setTopMargin(25);
            design.setBottomMargin(25);
            design.setColumnWidth(782);
            design.setWhenNoDataType(WhenNoDataTypeEnum.ALL_SECTIONS_NO_DETAIL);
            JRDesignStyle estilo = new JRDesignStyle();
            estilo.setName("Base");
            estilo.setDefault(true);
            estilo.setFontName("DejaVu Sans");
            estilo.setFontSize(9f);
            design.addStyle(estilo);
            for (String nombre : List.of("FILTRO", "RESUMEN")) {
                JRDesignParameter parametro = new JRDesignParameter();
                parametro.setName(nombre);
                parametro.setValueClass(String.class);
                design.addParameter(parametro);
            }
            JRDesignBand titulo = new JRDesignBand();
            titulo.setHeight(88);
            titulo.addElement(texto("\"Gestión de Personas\"", 0, 0, 782, 30, 21, true));
            titulo.addElement(texto("$P{FILTRO}", 0, 35, 782, 30, 10, false));
            titulo.addElement(texto("$P{RESUMEN}", 0, 68, 782, 16, 9, false));
            design.setTitle(titulo);

            String[] campos = {"id", "nombre", "apellido", "dni", "email", "telefono"};
            String[] nombres = {"ID", "Nombre", "Apellido", "DNI", "Correo", "Teléfono"};
            int[] anchos = {42, 136, 136, 88, 250, 130};
            JRDesignBand cabecera = new JRDesignBand();
            cabecera.setHeight(27);
            JRDesignBand detalle = new JRDesignBand();
            detalle.setHeight(28);
            detalle.setSplitType(SplitTypeEnum.STRETCH);
            int x = 0;
            for (int i = 0; i < campos.length; i++) {
                JRDesignField field = new JRDesignField();
                field.setName(campos[i]);
                field.setValueClass(i == 0 ? Long.class : String.class);
                design.addField(field);
                JRDesignTextField encabezado = texto("\"" + nombres[i] + "\"", x, 0, anchos[i], 25, 10, true);
                encabezado.setMode(ModeEnum.OPAQUE);
                encabezado.setBackcolor(new Color(229, 237, 247));
                cabecera.addElement(encabezado);
                JRDesignTextField dato = texto("$F{" + campos[i] + "}", x, 2, anchos[i], 24, 9, false);
                dato.setBlankWhenNull(true);
                dato.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
                detalle.addElement(dato);
                x += anchos[i];
            }
            design.setColumnHeader(cabecera);
            ((JRDesignSection) design.getDetailSection()).addBand(detalle);
            JRDesignBand pie = new JRDesignBand();
            pie.setHeight(20);
            pie.addElement(texto("\"Página \" + $V{PAGE_NUMBER}", 680, 0, 102, 20, 9, false));
            design.setPageFooter(pie);
            informe = JasperCompileManager.compileReport(design);
            return informe;
        }
    }

    private JRDesignTextField texto(String expresion, int x, int y, int ancho, int alto, int tamano, boolean negrita) {
        JRDesignTextField campo = new JRDesignTextField();
        campo.setX(x);
        campo.setY(y);
        campo.setWidth(ancho);
        campo.setHeight(alto);
        campo.setFontSize((float) tamano);
        campo.setBold(negrita);
        campo.getLineBox().setLeftPadding(4);
        campo.getLineBox().setRightPadding(4);
        campo.setExpression(new JRDesignExpression(expresion));
        return campo;
    }
}
