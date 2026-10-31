package ec.janery.common;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    public record ApiError(String codigo, String mensaje, List<String> detalles, String correlacion) {
    }

    @ExceptionHandler(NegocioException.class)
    ResponseEntity<ApiError> negocio(NegocioException e) {
        return respuesta(e.getStatus(), e.getCodigo(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validacion(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Los datos enviados no son válidos", detalles);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> sinPermiso(AccessDeniedException e) {
        return respuesta(HttpStatus.FORBIDDEN, "SIN_PERMISO", "No tiene permiso para esta acción", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integridad(DataIntegrityViolationException e) {
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "CONFLICTO", "La operación entra en conflicto con datos existentes", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> rutaInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Recurso no encontrado", List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> estado(ResponseStatusException e) {
        return respuesta(e.getStatusCode(), "SOLICITUD_INVALIDA", "Solicitud no válida", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> inesperado(Exception e) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse errorSpring) {
            return respuesta(errorSpring.getStatusCode(), "SOLICITUD_INVALIDA", "Solicitud no válida", List.of());
        }
        if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return respuesta(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "Solicitud no válida", List.of());
        }
        String correlacion = UUID.randomUUID().toString();
        log.error("Error inesperado [{}]", correlacion, e);
        return ResponseEntity.internalServerError()
                .body(new ApiError("ERROR_INTERNO", "Ocurrió un error interno", List.of(), correlacion));
    }

    private ResponseEntity<ApiError> respuesta(HttpStatusCode status, String codigo, String mensaje, List<String> detalles) {
        return ResponseEntity.status(status)
                .body(new ApiError(codigo, mensaje, detalles, UUID.randomUUID().toString()));
    }
}
