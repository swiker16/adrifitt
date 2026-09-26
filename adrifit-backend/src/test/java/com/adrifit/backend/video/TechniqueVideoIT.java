package com.adrifit.backend.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

class TechniqueVideoIT extends AbstractIntegrationTest {

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
    void clientSendsVideoTrainerCorrectsIt() {
        ResponseEntity<Map<String, Object>> uploaded = upload("/api/videos/me", video("isom", 4096), "Sentadilla",
                "¿Bajo suficiente?", client);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uploaded.getBody()).containsEntry("contentType", "video/mp4").containsEntry("source", "CLIENT")
                .containsEntry("exerciseName", "Sentadilla").containsEntry("durationSeconds", 42);
        Long videoId = id(uploaded);

        // Trainer inbox and badge.
        List<Map<String, Object>> pending = getList("/api/videos?pending=true", trainer).getBody();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0)).containsEntry("clientName", "Test User");
        assertThat(get("/api/videos/counts", trainer).getBody()).containsEntry("pendingReview", 1);
        assertThat(get("/api/dashboard/trainer", trainer).getBody()).containsEntry("pendingVideos", 1);

        ResponseEntity<Map<String, Object>> reviewed = patch("/api/videos/" + videoId + "/feedback",
                Map.of("feedback", "Baja un poco más y mantén el pecho arriba"), trainer);
        assertThat(reviewed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewed.getBody().get("reviewedAt")).isNotNull();
        assertThat(getList("/api/videos?pending=true", trainer).getBody()).isEmpty();

        // The correction is new for the client until they open the page.
        Map<String, Object> mine = getList("/api/videos/me", client).getBody().get(0);
        assertThat(mine).containsEntry("feedback", "Baja un poco más y mantén el pecho arriba").containsEntry("unseen", true);
        assertThat(get("/api/dashboard/client", client).getBody()).containsEntry("newVideos", 1);
        assertThat(post("/api/videos/me/seen", Map.of(), client).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/dashboard/client", client).getBody()).containsEntry("newVideos", 0);

        // Only client videos can be corrected; feedback is required.
        assertThat(patch("/api/videos/" + videoId + "/feedback", Map.of("feedback", " "), trainer).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signedLinkStreamsWithRangeRequests() {
        byte[] bytes = video("isom", 5000);
        Long videoId = id(upload("/api/videos/me", bytes, "Press banca", null, client));

        Map<String, Object> link = get("/api/videos/" + videoId + "/link", client).getBody();
        String url = (String) link.get("url");
        assertThat(url).contains("ngsw-bypass=true");

        // No JWT: the signature is the credential. Browsers seek with Range requests.
        HttpHeaders range = new HttpHeaders();
        range.set(HttpHeaders.RANGE, "bytes=0-99");
        ResponseEntity<byte[]> partial = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(range), byte[].class);
        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(partial.getBody()).hasSize(100);
        assertThat(partial.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-99/5000");

        ResponseEntity<byte[]> full = rest.getForEntity(url, byte[].class);
        assertThat(full.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(full.getBody()).isEqualTo(bytes);

        // Tampered or foreign signatures look like a missing video.
        assertThat(rest.getForEntity(url.replace("sig=", "sig=x"), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/videos/" + videoId + "/stream?exp=1&sig=abc", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Another client can't get a link to it.
        createClient(trainer, "otro", "otro@mail.com", "otro12345");
        String other = login("otro", "otro12345");
        assertThat(get("/api/videos/" + videoId + "/link", other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/videos/" + videoId + "/link", trainer).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trainerSendsDemonstrationVideo() {
        ResponseEntity<Map<String, Object>> sent = upload("/api/clients/" + clientId + "/videos", video("qt  ", 3000),
                "Peso muerto rumano", "Fíjate en la cadera", trainer);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody()).containsEntry("source", "TRAINER").containsEntry("contentType", "video/quicktime");
        Long videoId = id(sent);

        Map<String, Object> mine = getList("/api/videos/me", client).getBody().get(0);
        assertThat(mine).containsEntry("source", "TRAINER").containsEntry("unseen", true);

        // iPhone .mov is announced as mp4 for playback, kept as .mov when downloading.
        Map<String, Object> link = get("/api/videos/" + videoId + "/link", client).getBody();
        ResponseEntity<byte[]> play = rest.getForEntity((String) link.get("url"), byte[].class);
        assertThat(play.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("video/mp4"));
        ResponseEntity<byte[]> download = rest.getForEntity((String) link.get("downloadUrl"), byte[].class);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("video/quicktime"));
        assertThat(download.getHeaders().getContentDisposition().getFilename()).startsWith("Peso-muerto-rumano-").endsWith(".mov");

        // The client can't delete the trainer's video nor correct anything.
        assertThat(delete("/api/videos/" + videoId, client).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(patch("/api/videos/" + videoId + "/feedback", Map.of("feedback", "ok"), client).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch("/api/videos/" + videoId + "/feedback", Map.of("feedback", "ok"), trainer).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete("/api/videos/" + videoId, trainer).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void validatesFileAndExercise() {
        assertThat(upload("/api/videos/me", "not a video at all".getBytes(StandardCharsets.UTF_8), "Sentadilla", null, client)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(upload("/api/videos/me", video("isom", 100), " ", null, client).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Long own = id(upload("/api/videos/me", video("isom", 100), "Dominadas", null, client));
        assertThat(delete("/api/videos/" + own, client).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getList("/api/videos/me", client).getBody()).isEmpty();
    }

    @Test
    void deletingTheClientRemovesItsVideos() {
        upload("/api/videos/me", video("isom", 100), "Sentadilla", null, client);
        assertThat(delete("/api/clients/" + clientId, trainer).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM technique_videos")).isZero();
    }

    /** Minimal ISO base media header ("ftyp" box with the given brand) followed by filler bytes. */
    private static byte[] video(String brand, int size) {
        byte[] bytes = new byte[size];
        byte[] head = ("\0\0\0\u0018ftyp" + brand).getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(head, 0, bytes, 0, head.length);
        for (int i = head.length; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private ResponseEntity<Map<String, Object>> upload(String url, byte[] bytes, String exercise, String note, String token) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders part = new HttpHeaders();
        part.setContentType(MediaType.parseMediaType("video/mp4"));
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "clip.mp4";
            }
        }, part));
        body.add("exerciseName", exercise);
        if (note != null) {
            body.add("note", note);
        }
        body.add("durationSeconds", "42");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }
}
