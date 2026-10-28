package dev.memit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test","spring.jpa.hibernate.ddl-auto=create-drop"}) @AutoConfigureMockMvc
class ApiTest {
 @Autowired MockMvc mvc; @Autowired PhotoRepository photos; @Autowired ImageService images;
 @Test void flow() throws Exception {
  
  Long last=null;
  for(int i=0;i<2;i++){
   var bytes=new ByteArrayOutputStream();
   ImageIO.write(new BufferedImage(i==0?320:480,i==0?480:320,BufferedImage.TYPE_INT_RGB),"png",bytes);
   mvc.perform(multipart("/api/photos").file(new MockMultipartFile("file","x.png","image/png",bytes.toByteArray())).param("senderId","1").param("recipientId","2")).andExpect(status().isCreated()).andExpect(jsonPath("$.imageUrl").exists());
   Photo p=photos.findFirstByRecipientIdOrderByCreatedAtDescIdDesc(2L).orElseThrow();last=p.id;
   org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.size(images.root.resolve(p.imagePath))>0);
   mvc.perform(get("/api/photos/"+p.id+"/image")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
   BufferedImage output=ImageIO.read(images.root.resolve(p.imagePath).toFile());
   org.junit.jupiter.api.Assertions.assertEquals(320,output.getWidth());
   org.junit.jupiter.api.Assertions.assertEquals(320,output.getHeight());
   byte[] first=mvc.perform(get("/api/photos/"+p.id+"/image")).andReturn().getResponse().getContentAsByteArray();
   byte[] second=mvc.perform(get("/api/photos/"+p.id+"/image")).andReturn().getResponse().getContentAsByteArray();
   org.junit.jupiter.api.Assertions.assertArrayEquals(first,second);
   java.nio.file.Files.delete(images.root.resolve(p.imagePath));
  }
  mvc.perform(get("/api/photos/latest").param("userId","2")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(last));
  mvc.perform(get("/api/photos/latest").param("userId","999")).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/photos").file(new MockMultipartFile("file","x.png","image/png",new byte[]{1,2})).param("senderId","1").param("recipientId","2")).andExpect(status().isBadRequest());
 }
}
