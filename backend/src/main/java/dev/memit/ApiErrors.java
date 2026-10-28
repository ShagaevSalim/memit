package dev.memit;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.*;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",ObjectsText(e.getReason())));}
 private String ObjectsText(String s){return s==null?"Ошибка запроса":s;}
 @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> large(){return ResponseEntity.status(413).body(Map.of("message","Максимальный размер файла — 10 МБ"));}
 @ExceptionHandler({ServletRequestBindingException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.multipart.MultipartException.class}) ResponseEntity<?> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Проверьте multipart file, senderId, recipientId или userId"));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("Request failed",e);return ResponseEntity.internalServerError().body(Map.of("message","Внутренняя ошибка сервера"));}
}
