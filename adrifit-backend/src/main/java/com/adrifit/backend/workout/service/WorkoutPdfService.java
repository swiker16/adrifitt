package com.adrifit.backend.workout.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.workout.domain.Workout;
import com.adrifit.backend.workout.domain.WorkoutExercise;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutPdfService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutPdfService.class);

    private static final Color ORANGE = new Color(0xF9, 0x73, 0x16);
    private static final Color DARK   = new Color(0x1E, 0x29, 0x3B);
    private static final Color GRAY   = new Color(0x64, 0x74, 0x8B);
    private static final Color LIGHT  = new Color(0xF8, 0xFA, 0xFC);

    private final WorkoutService workoutService;
    private final ClientService clientService;

    public WorkoutPdfService(WorkoutService workoutService, ClientService clientService) {
        this.workoutService = workoutService;
        this.clientService = clientService;
    }

    @Transactional(readOnly = true)
    public byte[] generateWorkoutPdf(Long workoutId, Long clientId) {
        try {
            Workout workout = workoutService.getEntityById(workoutId);
            Client client = clientService.getEntityById(clientId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 60, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document, client, workout);
            addExerciseTable(document, workout);
            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF for workoutId={} clientId={}: {}", workoutId, clientId, e.getMessage(), e);
            throw new RuntimeException("Error generating PDF: " + e.getMessage(), e);
        }
    }

    private void addHeader(Document doc, Client client, Workout workout) {
        try {
            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ORANGE);
            Paragraph brand = new Paragraph("AdriFit", brandFont);
            brand.setAlignment(Element.ALIGN_CENTER);
            brand.setSpacingAfter(4);
            doc.add(brand);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, GRAY);
            Paragraph sub = new Paragraph("Plataforma de entrenamiento personal", subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(20);
            doc.add(sub);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, DARK);
            Paragraph title = new Paragraph(workout.getName(), titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            title.setSpacingAfter(4);
            doc.add(title);

            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, GRAY);
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            Paragraph meta = new Paragraph(
                    "Cliente: " + client.getFirstName() + " " + client.getLastName()
                    + "   ·   Generado: " + date, metaFont);
            meta.setSpacingAfter(6);
            doc.add(meta);

            if (workout.getObjective() != null && !workout.getObjective().isBlank()) {
                Font objFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, GRAY);
                Paragraph obj = new Paragraph("Objetivo: " + workout.getObjective(), objFont);
                obj.setSpacingAfter(20);
                doc.add(obj);
            } else {
                Paragraph sp = new Paragraph(" ");
                sp.setSpacingAfter(12);
                doc.add(sp);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error building PDF header", e);
        }
    }

    private void addExerciseTable(Document doc, Workout workout) {
        try {
            List<WorkoutExercise> exercises = workout.getExercises();
            boolean hasDays = exercises.stream().anyMatch(e -> e.getDayNumber() != null);

            if (hasDays) {
                // Group by dayNumber
                java.util.Map<Integer, List<WorkoutExercise>> grouped = new java.util.LinkedHashMap<>();
                for (WorkoutExercise ex : exercises) {
                    int day = ex.getDayNumber() != null ? ex.getDayNumber() : 1;
                    grouped.computeIfAbsent(day, k -> new java.util.ArrayList<>()).add(ex);
                }
                for (java.util.Map.Entry<Integer, List<WorkoutExercise>> entry : grouped.entrySet()) {
                    int dayNum = entry.getKey();
                    List<WorkoutExercise> dayExs = entry.getValue();
                    String dayName = dayExs.get(0).getDayName() != null ? dayExs.get(0).getDayName() : "Día " + dayNum;

                    Font dayFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, DARK);
                    Paragraph dayTitle = new Paragraph("DÍA " + dayNum + " — " + dayName, dayFont);
                    dayTitle.setSpacingBefore(16);
                    dayTitle.setSpacingAfter(6);
                    doc.add(dayTitle);

                    addDayTable(doc, dayExs);
                }
            } else {
                addDayTable(doc, exercises);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error building PDF table", e);
        }
    }

    private void addDayTable(Document doc, java.util.List<WorkoutExercise> exercises) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{3f, 2f, 1.5f, 1f, 1f, 4f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        String[] headers = {"Ejercicio", "Aproximaciones", "Reps aprox.", "Series", "Reps", "Notas"};
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(ORANGE);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 9, DARK);
        boolean alt = false;
        for (WorkoutExercise ex : exercises) {
            Color bg = alt ? LIGHT : Color.WHITE;
            addCell(table, ex.getExerciseName(), rowFont, bg);
            addCell(table, ex.getWarmUpSets() != null ? ex.getWarmUpSets() : "—", rowFont, bg);
            addCell(table, ex.getApproxReps() != null ? ex.getApproxReps() : "—", rowFont, bg);
            addCell(table, String.valueOf(ex.getSets()), rowFont, bg);
            addCell(table, ex.getReps() != null ? String.valueOf(ex.getReps()) : "—", rowFont, bg);
            addCell(table, ex.getNotes() != null ? ex.getNotes() : "—", rowFont, bg);
            alt = !alt;
        }

        doc.add(table);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addFooter(Document doc) {
        try {
            Font footFont = FontFactory.getFont(FontFactory.HELVETICA, 8, GRAY);
            Paragraph footer = new Paragraph("Generado por AdriFit · adrifit.app", footFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(24);
            doc.add(footer);
        } catch (Exception e) {
            throw new RuntimeException("Error building PDF footer", e);
        }
    }
}
