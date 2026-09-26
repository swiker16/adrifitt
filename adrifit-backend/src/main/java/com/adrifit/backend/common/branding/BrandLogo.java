package com.adrifit.backend.common.branding;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** AdriFitt logo (classpath {@code branding/logo.png}) for generated PDFs. */
public final class BrandLogo {

    private static final Logger log = LoggerFactory.getLogger(BrandLogo.class);
    private static volatile byte[] bytes;

    private BrandLogo() {
    }

    /** Adds the centred logo at {@code size} points; a missing logo never breaks the PDF. */
    public static void addTo(Document doc, float size) {
        try {
            Image logo = Image.getInstance(load());
            logo.scaleToFit(size, size);
            logo.setAlignment(Element.ALIGN_CENTER);
            doc.add(logo);
        } catch (Exception e) {
            log.warn("Could not add the logo to the PDF: {}", e.getMessage());
        }
    }

    private static byte[] load() throws Exception {
        if (bytes == null) {
            try (InputStream in = BrandLogo.class.getResourceAsStream("/branding/logo.png")) {
                if (in == null) {
                    throw new IllegalStateException("branding/logo.png not found");
                }
                bytes = in.readAllBytes();
            }
        }
        return bytes;
    }
}
