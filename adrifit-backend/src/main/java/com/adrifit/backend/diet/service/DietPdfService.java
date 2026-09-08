package com.adrifit.backend.diet.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.diet.domain.ClientDiet;
import com.adrifit.backend.diet.domain.Diet;
import com.adrifit.backend.diet.domain.DietAlternative;
import com.adrifit.backend.diet.domain.DietDay;
import com.adrifit.backend.diet.domain.DietFood;
import com.adrifit.backend.diet.domain.DietMeal;
import com.adrifit.backend.diet.repository.ClientDietRepository;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DietPdfService {

    private static final Logger log = LoggerFactory.getLogger(DietPdfService.class);

    private static final Color ORANGE = new Color(0xF9, 0x73, 0x16);
    private static final Color DARK   = new Color(0x1E, 0x29, 0x3B);
    private static final Color GRAY   = new Color(0x64, 0x74, 0x8B);
    private static final Color LIGHT  = new Color(0xF8, 0xFA, 0xFC);
    private final ClientService clientService;
    private final ClientDietRepository clientDietRepository;

    public DietPdfService(ClientService clientService,
                          ClientDietRepository clientDietRepository) {
        this.clientService = clientService;
        this.clientDietRepository = clientDietRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generateForClient(Long clientId) {
        Client client = clientService.getEntityById(clientId);
        ClientDiet cd = clientDietRepository.findByClient_IdAndActiveTrue(clientId)
                .orElseThrow(() -> new IllegalStateException("Cliente sin dieta activa"));
        return generate(client, cd.getDiet(), cd);
    }

    @Transactional(readOnly = true)
    public byte[] generateForCurrentClient(Long clientId) {
        return generateForClient(clientId);
    }

    private byte[] generate(Client client, Diet diet, ClientDiet cd) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, client, diet, cd);
            addDietBody(doc, diet);
            addFooter(doc);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating diet PDF for clientId={}: {}", client.getId(), e.getMessage(), e);
            throw new RuntimeException("Error generating diet PDF: " + e.getMessage(), e);
        }
    }

    private void addHeader(Document doc, Client client, Diet diet, ClientDiet cd) throws Exception {
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, ORANGE);
        Paragraph brand = new Paragraph("AdriFit", brandFont);
        brand.setAlignment(Element.ALIGN_CENTER);
        brand.setSpacingAfter(4);
        doc.add(brand);

        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, GRAY);
        Paragraph sub = new Paragraph("Plan de nutrición personalizado", subFont);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(20);
        doc.add(sub);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, DARK);
        Paragraph title = new Paragraph(diet.getName(), titleFont);
        title.setSpacingAfter(4);
        doc.add(title);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, GRAY);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String assignedStr = cd.getAssignedAt() != null
                ? LocalDate.ofInstant(cd.getAssignedAt(), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "—";
        doc.add(new Paragraph("Cliente: " + client.getFirstName() + " " + client.getLastName()
                + "   ·   Generado: " + date + "   ·   Asignada: " + assignedStr, metaFont));

        if (diet.getObjective() != null && !diet.getObjective().isBlank()) {
            Font objFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, GRAY);
            Paragraph obj = new Paragraph("Objetivo: " + diet.getObjective(), objFont);
            obj.setSpacingAfter(16);
            doc.add(obj);
        }

        if (cd.getTrainerNotes() != null && !cd.getTrainerNotes().isBlank()) {
            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, GRAY);
            Paragraph note = new Paragraph("Notas del entrenador: " + cd.getTrainerNotes(), noteFont);
            note.setSpacingAfter(16);
            doc.add(note);
        }
    }

    private void addDietBody(Document doc, Diet diet) throws Exception {
        if (diet.getDays() == null) return;
        Font dayFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, DARK);
        Font mealFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DARK);
        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, GRAY);

        for (DietDay day : diet.getDays()) {
            Paragraph dayTitle = new Paragraph("DÍA " + (day.getDayNumber() != null ? day.getDayNumber() : (day.getOrderIndex() + 1))
                    + " — " + day.getName(), dayFont);
            dayTitle.setSpacingBefore(18);
            dayTitle.setSpacingAfter(4);
            doc.add(dayTitle);

            if (day.getNotes() != null && !day.getNotes().isBlank()) {
                doc.add(new Paragraph(day.getNotes(), noteFont));
            }

            if (day.getMeals() == null) continue;
            for (DietMeal meal : day.getMeals()) {
                Paragraph mealTitle = new Paragraph(
                        (meal.getTime() != null && !meal.getTime().isBlank()
                                ? meal.getTime() + " — " : "")
                                + meal.getName(), mealFont);
                mealTitle.setSpacingBefore(10);
                mealTitle.setSpacingAfter(4);
                doc.add(mealTitle);

                if (meal.getNotes() != null && !meal.getNotes().isBlank()) {
                    doc.add(new Paragraph(meal.getNotes(), noteFont));
                }

                if (meal.getFoods() != null && !meal.getFoods().isEmpty()) {
                    addFoodTable(doc, meal);
                }
            }
        }
    }

    private void addFoodTable(Document doc, DietMeal meal) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 1f, 1f, 1f, 1f, 3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);

        String[] headers = {"Alimento", "Cantidad", "Kcal", "Prot.", "HC", "Grasas", "Alternativas / Notas"};
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(ORANGE);
            cell.setPadding(5);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK);
        boolean alt = false;
        for (DietFood food : meal.getFoods()) {
            Color bg = alt ? LIGHT : Color.WHITE;
            addCell(table, food.getFoodName(), rowFont, bg);
            addCell(table, fmtQty(food.getQuantity(), food.getUnit()), rowFont, bg);
            addCell(table, fmtNum(food.getCalories()), rowFont, bg);
            addCell(table, fmtNum(food.getProteinGrams()), rowFont, bg);
            addCell(table, fmtNum(food.getCarbsGrams()), rowFont, bg);
            addCell(table, fmtNum(food.getFatGrams()), rowFont, bg);
            addCell(table, buildAltNotes(food), rowFont, bg);
            alt = !alt;
        }
        doc.add(table);
    }

    private String buildAltNotes(DietFood food) {
        StringBuilder sb = new StringBuilder();
        if (food.getNotes() != null && !food.getNotes().isBlank()) sb.append(food.getNotes());
        if (food.getAlternatives() != null) {
            for (DietAlternative a : food.getAlternatives()) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(a.getAlternativeName());
                if (a.getQuantity() != null) sb.append(" ").append(fmtQty(a.getQuantity(), a.getUnit()));
            }
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private void addFooter(Document doc) throws Exception {
        Font footFont = FontFactory.getFont(FontFactory.HELVETICA, 8, GRAY);
        Paragraph footer = new Paragraph("Generado por AdriFit · adrifit.app", footFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(24);
        doc.add(footer);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private String fmtNum(BigDecimal val) {
        return val != null ? val.stripTrailingZeros().toPlainString() : "—";
    }

    private String fmtQty(BigDecimal qty, String unit) {
        if (qty == null) return unit != null ? unit : "—";
        String q = qty.stripTrailingZeros().toPlainString();
        return unit != null ? q + " " + unit : q;
    }
}
