package dev.memit;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
@Service
public class ImageService {
 private static final String[] TEXTS={"Я В ШОКЕ","СНОВА ПОНЕДЕЛЬНИК","ПОМОГИТЕ","ЭТО Я","ЖИЗА","КАК ЖЕ ТАК","НУ И ЛАДНО","МНЕ НОРМ","Я УСТАЛ","ВОТ ТАК ВОТ"};
 final Path root=Path.of("uploads").toAbsolutePath().normalize();
 private Font font;
 public ImageService() throws IOException {
  Files.createDirectories(root);
  font=new Font(Font.SANS_SERIF,Font.BOLD,32);
  Path custom=Path.of("fonts/Impact.ttf");
  if(Files.isRegularFile(custom)) { try(InputStream in=Files.newInputStream(custom)){font=Font.createFont(Font.TRUETYPE_FONT,in);} catch(Exception e){throw new IOException("Cannot load fonts/Impact.ttf",e);} }
  else org.slf4j.LoggerFactory.getLogger(getClass()).warn("Impact missing: using bold SansSerif. Place licensed font at fonts/Impact.ttf");
 }
 public void process(MultipartFile file,Photo photo) throws IOException {
  if(file.isEmpty() || file.getSize()>10*1024*1024) throw bad("Требуется JPEG или PNG размером до 10 МБ");
  BufferedImage source;
  try(ImageInputStream input=ImageIO.createImageInputStream(file.getInputStream())) {
   var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw bad("Файл не является изображением");
   ImageReader reader=readers.next();
   try { reader.setInput(input); String format=reader.getFormatName(); if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG")) throw bad("Разрешены JPEG и PNG");
    int w=reader.getWidth(0),h=reader.getHeight(0); if(w<32||h<32||(long)w*h>20000000) throw bad("Размер изображения: минимум 32×32, максимум 20 мегапикселей"); source=reader.read(0);
   } finally {reader.dispose();}
  } catch(ResponseStatusException e){throw e;} catch(IOException|RuntimeException e){throw bad("Не удалось прочитать изображение");}
  var random=ThreadLocalRandom.current(); photo.overlayText=TEXTS[random.nextInt(TEXTS.length)]; boolean gray=random.nextBoolean(); photo.filter=gray?"BLACK_AND_WHITE":"ORIGINAL";
  int side=Math.min(source.getWidth(),source.getHeight());
  source=source.getSubimage((source.getWidth()-side)/2,(source.getHeight()-side)/2,side,side);
  int w=side,h=side; BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
  Graphics2D g=result.createGraphics();
  try {
   g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(source,0,0,null);
   if(gray) for(int y=0;y<h;y++) for(int x=0;x<w;x++){int rgb=result.getRGB(x,y);int v=(int)(0.299*((rgb>>16)&255)+0.587*((rgb>>8)&255)+0.114*(rgb&255));result.setRGB(x,y,(v<<16)|(v<<8)|v);}
   g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   int margin=Math.max(2,w/25),size=Math.max(4,Math.min(w/9,h/6)); java.util.List<String> lines;
   while(true){g.setFont(font.deriveFont((float)size)); lines=new ArrayList<>();String line="";for(String word:photo.overlayText.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(g.getFontMetrics().stringWidth(next)>w-2*margin&&!line.isEmpty()){lines.add(line);line=word;}else line=next;}lines.add(line);boolean fits=lines.stream().allMatch(s->g.getFontMetrics().stringWidth(s)<=w-2*margin);if(size<=2||(fits&&lines.size()*g.getFontMetrics().getHeight()<h*0.6))break;size--;}
   int lineHeight=g.getFontMetrics().getHeight();
   int blockHeight=lines.size()*lineHeight;
   int top=switch(random.nextInt(3)) {
    case 0 -> margin;
    case 1 -> (h-blockHeight)/2;
    default -> h-margin-blockHeight;
   };
   int y=top+g.getFontMetrics().getAscent();
   for(String line:lines){TextLayout layout=new TextLayout(line,g.getFont(),g.getFontRenderContext());Shape shape=layout.getOutline(AffineTransform.getTranslateInstance((w-layout.getAdvance())/2,y));g.setColor(Color.BLACK);g.setStroke(new BasicStroke(Math.max(1,size/12f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);g.setColor(Color.WHITE);g.fill(shape);y+=lineHeight;}
  } finally {g.dispose();}
  photo.imagePath=UUID.randomUUID()+".jpg"; Path target=root.resolve(photo.imagePath);
  try {if(!ImageIO.write(result,"JPEG",target.toFile()))throw new IOException("JPEG writer unavailable");} catch(IOException e){Files.deleteIfExists(target);throw e;}
 }
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
