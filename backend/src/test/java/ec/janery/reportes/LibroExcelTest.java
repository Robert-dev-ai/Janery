package ec.janery.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

class LibroExcelTest {

    @Test
    void columnasComoEnExcel() {
        assertThat(LibroExcel.columna(0)).isEqualTo("A");
        assertThat(LibroExcel.columna(25)).isEqualTo("Z");
        assertThat(LibroExcel.columna(26)).isEqualTo("AA");
        assertThat(LibroExcel.columna(701)).isEqualTo("ZZ");
    }

    @Test
    void textosEscapadosYNuncaFormulas() throws Exception {
        LibroExcel libro = new LibroExcel();
        libro.hoja("Hoja/rara:con*nombre?muy-largo-de-mas-de-31").encabezado("A & B")
                .fila(LibroExcel.texto("=HYPERLINK(\"x\")<\u0001>"), LibroExcel.dinero(new BigDecimal("-1.50")));
        Map<String, String> partes = leer(libro.escribir());

        String hoja = partes.get("xl/worksheets/sheet1.xml");
        assertThat(hoja).contains("A &amp; B", "t=\"inlineStr\"", "=HYPERLINK(&quot;x&quot;)&lt;&gt;", "<v>-1.50</v>")
                .doesNotContain("<f>", "\u0001");
        assertThat(partes.get("xl/workbook.xml")).contains("name=\"Hoja rara con nombre muy-largo-\"");
    }

    private static Map<String, String> leer(byte[] xlsx) throws Exception {
        Map<String, String> partes = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                partes.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return partes;
    }
}
