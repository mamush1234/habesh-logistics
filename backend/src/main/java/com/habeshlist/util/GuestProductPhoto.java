package com.habeshlist.util;

import com.habeshlist.exception.BadRequestException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

public final class GuestProductPhoto {
    private GuestProductPhoto() {}

    public static String prepare(String dataUrl) {
        if (dataUrl == null || dataUrl.length() > 3_000_000
                || !(dataUrl.startsWith("data:image/jpeg;base64,") || dataUrl.startsWith("data:image/png;base64,")))
            throw new BadRequestException("Choose a JPEG or PNG product photo under 2 MB");
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
            if (bytes.length > 2_000_000) throw new BadRequestException("Product photo must be under 2 MB");
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new BadRequestException("Product photo is not a readable image");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
                        throw new BadRequestException("Choose a JPEG or PNG product photo");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000)
                        throw new BadRequestException("Product photo must be no larger than 4096 by 4096 pixels");
                    BufferedImage image = reader.read(0);
                    BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    var graphics = rgb.createGraphics();
                    try { graphics.drawImage(image, 0, 0, null); }
                    finally { graphics.dispose(); }
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(rgb, "jpeg", output)) throw new IllegalStateException("JPEG encoder unavailable");
                    if (output.size() > 2_000_000) throw new BadRequestException("Product photo must be under 2 MB");
                    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch (IllegalArgumentException | IOException ex) {
            throw new BadRequestException("Product photo is not a readable JPEG or PNG image");
        }
    }
}
