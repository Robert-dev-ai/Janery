package ec.janery.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record Pagina<T>(List<T> contenido, int pagina, int tamano, long total) {

    private static final int TAMANO_MAXIMO = 100;

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapper) {
        return new Pagina<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public static int limitar(int tamano) {
        return Math.clamp(tamano, 1, TAMANO_MAXIMO);
    }
}
