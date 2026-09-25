package com.adrifit.backend.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ProgressPhotoIT extends AbstractIntegrationTest {

    private String trainer;
    private String client;
    private Long clientId;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        client = login("ana", "ana12345");
    }

    @Test
    void uploadListViewAndDelete() throws Exception {
        ResponseEntity<Map<String, Object>> uploaded = upload(png(), "foto.png", "FRONT", client);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uploaded.getBody().get("contentType")).isEqualTo("image/png");
        Long photoId = id(uploaded);

        assertThat(getList("/api/photos/me", client).getBody()).hasSize(1);
        assertThat(getList("/api/clients/" + clientId + "/photos", trainer).getBody()).hasSize(1);

        ResponseEntity<byte[]> content = rest.exchange("/api/photos/" + photoId + "/content", HttpMethod.GET,
                auth(trainer), byte[].class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        // Another client can't see it.
        createClient(trainer, "otro", "otro@mail.com", "otro12345");
        String other = login("otro", "otro12345");
        assertThat(rest.exchange("/api/photos/" + photoId + "/content", HttpMethod.GET, auth(other), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(patch("/api/photos/" + photoId + "/comment", Map.of("comment", "Gran cambio"), trainer)
                .getBody().get("trainerComment")).isEqualTo("Gran cambio");

        assertThat(delete("/api/photos/me/" + photoId, client).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getList("/api/photos/me", client).getBody()).isEmpty();
    }

    @Test
    void nonImageIsRejectedEvenIfDeclaredAsImage() {
        ResponseEntity<Map<String, Object>> response = upload("%PDF-1.4 fake".getBytes(), "foto.png", "FRONT", client);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private ResponseEntity<Map<String, Object>> upload(byte[] bytes, String name, String pose, String token) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return name;
            }
        }, partHeaders));
        body.add("pose", pose);
        body.add("takenOn", java.time.LocalDate.now().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/photos/me", HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
