package ec.janery.reportes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Libro de Excel (.xlsx) mínimo, escrito a mano: hojas con texto, números y unos pocos estilos. Evita una dependencia
 * pesada para un solo informe. Los textos van como cadena en línea: Excel nunca los evalúa como fórmula.
 */
public final class LibroExcel {

    /** Índices de {@code cellXfs} en styles.xml. */
    public enum Estilo {
        NORMAL(0), ENCABEZADO(1), DINERO(2), DINERO_TOTAL(3), TITULO(4), NEGRITA(5), NUMERO(6), SUBTITULO(7);

        private final int indice;

        Estilo(int indice) {
            this.indice = indice;
        }
    }

    public record Celda(Object valor, Estilo estilo) {
    }

    public static final class Hoja {
        private final String nombre;
        private final List<List<Celda>> filas = new ArrayList<>();
        private final List<Integer> anchos = new ArrayList<>();
        private int filaFija;

        private Hoja(String nombre) {
            this.nombre = nombre;
        }

        public Hoja anchos(int... valores) {
            for (int v : valores) {
                anchos.add(v);
            }
            return this;
        }

        /** Las filas hasta la actual quedan fijas al desplazarse. */
        public Hoja fijarHastaAqui() {
            filaFija = filas.size();
            return this;
        }

        public Hoja fila(Celda... celdas) {
            filas.add(List.of(celdas));
            return this;
        }

        public Hoja fila(List<Celda> celdas) {
            filas.add(List.copyOf(celdas));
            return this;
        }

        public Hoja vacia() {
            filas.add(List.of());
            return this;
        }

        public Hoja encabezado(String... titulos) {
            List<Celda> celdas = new ArrayList<>();
            for (String t : titulos) {
                celdas.add(new Celda(t, Estilo.ENCABEZADO));
            }
            return fila(celdas);
        }
    }

    private final List<Hoja> hojas = new ArrayList<>();

    public Hoja hoja(String nombre) {
        Hoja h = new Hoja(nombre);
        hojas.add(h);
        return h;
    }

    public static Celda texto(String valor) {
        return new Celda(valor, Estilo.NORMAL);
    }

    public static Celda texto(String valor, Estilo estilo) {
        return new Celda(valor, estilo);
    }

    public static Celda dinero(BigDecimal valor) {
        return new Celda(valor, Estilo.DINERO);
    }

    public static Celda total(BigDecimal valor) {
        return new Celda(valor, Estilo.DINERO_TOTAL);
    }

    public static Celda numero(Number valor) {
        return new Celda(valor, Estilo.NUMERO);
    }

    public byte[] escribir() {
        var salida = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            agregar(zip, "[Content_Types].xml", tiposDeContenido());
            agregar(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            agregar(zip, "xl/workbook.xml", libro());
            agregar(zip, "xl/_rels/workbook.xml.rels", relacionesLibro());
            agregar(zip, "xl/styles.xml", ESTILOS);
            for (int i = 0; i < hojas.size(); i++) {
                agregar(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", hoja(hojas.get(i)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    private static void agregar(ZipOutputStream zip, String ruta, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(ruta));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String tiposDeContenido() {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                <Default Extension="xml" ContentType="application/xml"/>\
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
        for (int i = 1; i <= hojas.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String libro() {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""");
        for (int i = 1; i <= hojas.size(); i++) {
            sb.append("<sheet name=\"").append(xml(nombreHoja(hojas.get(i - 1).nombre))).append("\" sheetId=\"").append(i)
                    .append("\" r:id=\"rId").append(i).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String relacionesLibro() {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""");
        for (int i = 1; i <= hojas.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(hojas.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static String hoja(Hoja h) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""");
        if (h.filaFija > 0) {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"").append(h.filaFija).append("\" topLeftCell=\"A")
                    .append(h.filaFija + 1).append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        }
        if (!h.anchos.isEmpty()) {
            sb.append("<cols>");
            for (int i = 0; i < h.anchos.size(); i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(h.anchos.get(i))
                        .append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (int f = 0; f < h.filas.size(); f++) {
            List<Celda> fila = h.filas.get(f);
            if (fila.isEmpty()) {
                continue;
            }
            sb.append("<row r=\"").append(f + 1).append("\">");
            for (int c = 0; c < fila.size(); c++) {
                celda(sb, columna(c) + (f + 1), fila.get(c));
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static void celda(StringBuilder sb, String ref, Celda celda) {
        if (celda == null) {
            return;
        }
        int s = celda.estilo().indice;
        Object v = celda.valor();
        if (v == null) {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"/>");
        } else if (v instanceof BigDecimal n) {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>").append(n.toPlainString()).append("</v></c>");
        } else if (v instanceof Number n) {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>").append(n).append("</v></c>");
        } else {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xml(v.toString())).append("</t></is></c>");
        }
    }

    static String columna(int indice) {
        StringBuilder sb = new StringBuilder();
        for (int n = indice + 1; n > 0; n = (n - 1) / 26) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return sb.toString();
    }

    /** Excel no admite estos caracteres en el nombre de la hoja ni más de 31 caracteres. */
    private static String nombreHoja(String nombre) {
        String limpio = nombre.replaceAll("[\\[\\]:*?/\\\\]", " ");
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }

    static String xml(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char ch : texto.toCharArray()) {
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    // XML 1.0 no admite caracteres de control, salvo tabulador y saltos de línea.
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final String ESTILOS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
            <numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0.00;[Red]-&quot;$&quot;#,##0.00"/></numFmts>\
            <fonts count="4">\
            <font><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="14"/><name val="Calibri"/><family val="2"/></font>\
            </fonts>\
            <fills count="4">\
            <fill><patternFill patternType="none"/></fill>\
            <fill><patternFill patternType="gray125"/></fill>\
            <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill>\
            <fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill>\
            </fills>\
            <borders count="2">\
            <border><left/><right/><top/><bottom/><diagonal/></border>\
            <border><left/><right/><top style="thin"><color auto="1"/></top><bottom style="double"><color auto="1"/></bottom><diagonal/></border>\
            </borders>\
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
            <cellXfs count="8">\
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
            <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment wrapText="1" vertical="center"/></xf>\
            <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>\
            <xf numFmtId="164" fontId="2" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>\
            <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
            <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>\
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
            <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
            </cellXfs>\
            <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>\
            </styleSheet>""";
}
