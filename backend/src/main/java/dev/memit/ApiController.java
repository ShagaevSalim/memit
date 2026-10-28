package dev.memit;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiController {
 private final UserRepository users; private final PhotoRepository photos;private final ImageService images;
 public ApiController(UserRepository u,PhotoRepository p,ImageService i){users=u;photos=p;images=i;}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP");}
 @GetMapping("/users") public Object users(){return users.findAll(org.springframework.data.domain.Sort.by("id"));}
 void check(Long id){if(!users.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Пользователь не найден: "+id);}
 record Metadata(Long id,Long senderId,Long recipientId,String overlayText,String filter,String imageUrl,Instant createdAt){}
 Metadata dto(Photo p){return new Metadata(p.id,p.senderId,p.recipientId,p.overlayText,p.filter,"/api/photos/"+p.id+"/image",p.createdAt);}
 @PostMapping(value="/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public Metadata upload(@RequestParam MultipartFile file,@RequestParam Long senderId,@RequestParam Long recipientId) throws java.io.IOException {
  check(senderId);check(recipientId);Photo p=new Photo();p.senderId=senderId;p.recipientId=recipientId;images.process(file,p);p.createdAt=Instant.now();
  try {return dto(photos.saveAndFlush(p));} catch(RuntimeException e){Files.deleteIfExists(images.root.resolve(p.imagePath));throw e;}
 }
 @GetMapping("/photos/latest") public Metadata latest(@RequestParam Long userId){check(userId);return dto(photos.findFirstByRecipientIdOrderByCreatedAtDescIdDesc(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"У пользователя пока нет фотографий")));}
 @GetMapping("/photos/{id}/image") public ResponseEntity<Resource> image(@PathVariable Long id){Photo p=photos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Фотография не найдена"));Path path=images.root.resolve(p.imagePath).normalize();if(!path.startsWith(images.root)||!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Файл не найден");return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noCache()).body(new FileSystemResource(path));}
}
