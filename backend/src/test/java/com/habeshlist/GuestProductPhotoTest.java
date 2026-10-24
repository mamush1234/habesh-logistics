package com.habeshlist;

import com.habeshlist.exception.BadRequestException;
import com.habeshlist.util.GuestProductPhoto;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuestProductPhotoTest {
    private byte[] png(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
    private String data(byte[] bytes) { return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes); }

    @Test
    void reencodesValidPhotoAsJpeg() throws Exception {
        String result = GuestProductPhoto.prepare(data(png(10, 10)));
        assertTrue(result.startsWith("data:image/jpeg;base64,"));
        var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.substring(result.indexOf(',') + 1))));
        assertEquals(10, image.getWidth());
        assertEquals(10, image.getHeight());
    }
    @Test
    void enforcesExactByteLimit() throws Exception {
        byte[] image = png(1, 1);
        assertNotNull(GuestProductPhoto.prepare(data(Arrays.copyOf(image, 2_000_000))));
        var error = assertThrows(BadRequestException.class,
                () -> GuestProductPhoto.prepare(data(Arrays.copyOf(image, 2_000_001))));
        assertEquals("Product photo must be under 2 MB", error.getMessage());
    }
    @Test
    void enforcesExactDimensionLimit() throws Exception {
        assertNotNull(GuestProductPhoto.prepare(data(png(4096, 1))));
        assertThrows(BadRequestException.class, () -> GuestProductPhoto.prepare(data(png(4097, 1))));
        assertThrows(BadRequestException.class, () -> GuestProductPhoto.prepare(data(png(1, 4097))));
        assertThrows(BadRequestException.class, () -> GuestProductPhoto.prepare(data(png(4001, 4000))));
    }
    @Test
    void rejectsUnsupportedOrUnreadableInput() {
        for (String input : List.of("data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,!",
                "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[10]),
                "data:image/png;base64," + "A".repeat(3_000_000))) {
            assertThrows(BadRequestException.class, () -> GuestProductPhoto.prepare(input));
        }
    }
}
