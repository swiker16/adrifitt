package com.adrifit.backend.email.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * HTML bodies for every transactional email. Kept in code (no template engine) because they are
 * short; every user-provided value goes through {@link #esc(String)}.
 */
@Component
public class EmailTemplates {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String publicUrl;

    public EmailTemplates(@Value("${adrifit.public-url:http://localhost:4200}") String publicUrl) {
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public String welcome(String firstName, String username, String temporaryPassword, String planName) {
        return layout("¡Bienvenido/a a AdriFitt!",
                p("Hola " + esc(firstName) + ",")
                        + p("Tu entrenador ha creado tu cuenta" + (planName != null ? " con el plan <strong>" + esc(planName) + "</strong>" : "") + ".")
                        + box("Usuario: <strong>" + esc(username) + "</strong><br>Contraseña temporal: <strong>" + esc(temporaryPassword) + "</strong>")
                        + p("Por seguridad, te pediremos que cambies la contraseña la primera vez que entres.")
                        + button("Entrar en mi área", publicUrl + "/login")
                        + p("<strong>📲 Llévala en el móvil:</strong> descarga la app de AdriFitt y tendrás tu rutina, tu dieta "
                                + "y el chat con tu entrenador en la pantalla de inicio, con avisos al momento.")
                        + button("Descargar la app", publicUrl + "/app"));
    }

    public String passwordReset(String firstName, String username, String temporaryPassword) {
        return layout("Tu contraseña se ha restablecido",
                p("Hola " + esc(firstName) + ",")
                        + p("Tu entrenador ha restablecido tu contraseña de acceso.")
                        + box("Usuario: <strong>" + esc(username) + "</strong><br>Nueva contraseña temporal: <strong>" + esc(temporaryPassword) + "</strong>")
                        + button("Entrar", publicUrl + "/login"));
    }

    public String paymentDue(String firstName, String concept, BigDecimal amount, LocalDate dueDate) {
        return layout("Tienes un pago pendiente",
                p("Hola " + esc(firstName) + ",")
                        + p("Se ha generado un nuevo cobro en tu cuenta:")
                        + box(esc(concept) + "<br>Importe: <strong>" + money(amount) + "</strong><br>Vencimiento: " + date(dueDate))
                        + p("Puedes pagarlo con tarjeta o Bizum desde tu área privada.")
                        + button("Pagar ahora", publicUrl + "/client/subscription"));
    }

    public String paymentReceipt(String firstName, String concept, BigDecimal amount, String methodLabel, String reference) {
        return layout("Pago recibido. ¡Gracias!",
                p("Hola " + esc(firstName) + ",")
                        + p("Hemos registrado correctamente tu pago.")
                        + box(esc(concept) + "<br>Importe: <strong>" + money(amount) + "</strong><br>Método: " + esc(methodLabel)
                        + (reference != null ? "<br>Referencia: " + esc(reference) : ""))
                        + button("Ver mis pagos", publicUrl + "/client/subscription"));
    }

    public String subscriptionChanged(String firstName, String headline, String detail) {
        return layout(headline,
                p("Hola " + esc(firstName) + ",")
                        + p(esc(detail))
                        + button("Ver mi suscripción", publicUrl + "/client/subscription"));
    }

    public String reportFeedback(String firstName, String feedback) {
        return layout("Tu entrenador ha revisado tu seguimiento",
                p("Hola " + esc(firstName) + ",")
                        + p("Ya tienes el feedback de tu último seguimiento:")
                        + box(esc(feedback).replace("\n", "<br>"))
                        + button("Ver mi seguimiento", publicUrl + "/client/report"));
    }

    public String reviewReminder(String firstName, LocalDate reviewDate) {
        return layout("Toca revisión",
                p("Hola " + esc(firstName) + ",")
                        + p("Tu próxima revisión es el <strong>" + date(reviewDate) + "</strong>. "
                        + "Envía tu seguimiento (peso, medidas, sensaciones y fotos) para que tu entrenador pueda ajustar tu plan.")
                        + button("Enviar seguimiento", publicUrl + "/client/report"));
    }

    public String custom(String firstName, String body) {
        return layout(null,
                p("Hola " + esc(firstName) + ",")
                        + p(esc(body).replace("\n", "<br>")));
    }

    // ── building blocks ─────────────────────────────────────────────────────

    private String layout(String title, String content) {
        return "<!doctype html><html><body style=\"margin:0;background:#f5f7fa;font-family:Arial,Helvetica,sans-serif;color:#111827\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden\">"
                + "<tr><td style=\"background:#080B0F;padding:20px 28px;font-size:22px;font-weight:800;color:#ffffff\">"
                + "<img src=\"" + publicUrl + "/brand/logo-96.png\" width=\"44\" height=\"44\" alt=\"\" style=\"vertical-align:middle;margin-right:10px;border:0\">"
                + "Adri<span style=\"color:#FF7A1A\">Fitt</span></td></tr>"
                + "<tr><td style=\"padding:28px\">"
                + (title != null ? "<h1 style=\"font-size:20px;margin:0 0 16px\">" + esc(title) + "</h1>" : "")
                + content
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:#f9fafb;color:#6B7280;font-size:12px\">Este es un mensaje automático de AdriFitt.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String p(String html) {
        return "<p style=\"margin:0 0 14px;line-height:1.5\">" + html + "</p>";
    }

    private String box(String html) {
        return "<div style=\"background:#fff7ed;border-left:4px solid #FF7A1A;border-radius:8px;padding:14px 16px;margin:0 0 16px;line-height:1.6\">"
                + html + "</div>";
    }

    private String button(String label, String url) {
        return "<p style=\"margin:20px 0 0\"><a href=\"" + esc(url) + "\" style=\"display:inline-block;background:#FF7A1A;color:#ffffff;"
                + "text-decoration:none;font-weight:bold;padding:12px 22px;border-radius:999px\">" + esc(label) + "</a></p>";
    }

    public static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    public static String money(BigDecimal amount) {
        return amount == null ? "—" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }

    private static String date(LocalDate date) {
        return date == null ? "—" : date.format(DATE);
    }
}
