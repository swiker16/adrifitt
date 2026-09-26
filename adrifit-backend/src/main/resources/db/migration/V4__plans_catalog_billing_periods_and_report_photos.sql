-- =============================================================================
-- V4 · New plan catalogue (Básica / Premium), billing periods, special prices
--      per client and photos attached to check-in reports.
-- Portable SQL: PostgreSQL and H2 (PostgreSQL mode).
-- =============================================================================

-- ─── Plans: price per billing period + feature list ─────────────────────────
ALTER TABLE plans ADD COLUMN IF NOT EXISTS quarterly_price NUMERIC(8, 2);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS semiannual_price NUMERIC(8, 2);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS annual_price NUMERIC(8, 2);
ALTER TABLE plans ADD COLUMN IF NOT EXISTS features TEXT;

-- ─── Subscriptions: billing period + special conditions ────────────────────
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS billing_period VARCHAR(20) DEFAULT 'MONTHLY' NOT NULL;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS custom_price NUMERIC(10, 2);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS custom_price_note VARCHAR(200);
ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_billing_period CHECK (billing_period IN ('MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));
ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_custom_price CHECK (custom_price IS NULL OR custom_price >= 0);

-- ─── Check-in reports carry 4-6 progress photos ─────────────────────────────
ALTER TABLE progress_photos ADD COLUMN IF NOT EXISTS report_id BIGINT;
ALTER TABLE progress_photos
    ADD CONSTRAINT fk_progress_photos_report FOREIGN KEY (report_id) REFERENCES weekly_reports (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_progress_photos_report ON progress_photos (report_id);

-- ─── Plan catalogue: Basic/Advanced/Premium → Básica/Premium ────────────────
-- Existing rows are converted in place so current subscriptions (and their
-- history/payments) keep pointing to a valid plan. Fresh databases have no
-- plans yet: DataInitializer seeds the same catalogue.
UPDATE plans SET
    name = 'Premium',
    description = 'Todo lo de la tarifa Básica más optimización fisiológica, control clínico avanzado, seguimiento semanal y soporte exclusivo.',
    monthly_price = 143.00,
    quarterly_price = 429.00,
    semiannual_price = 843.00,
    annual_price = 1573.00,
    review_frequency_days = 7,
    messaging_enabled = TRUE,
    analytics_enabled = TRUE,
    pdf_export_enabled = TRUE,
    priority_support = TRUE,
    active = TRUE,
    features = 'Todo lo incluido en la tarifa Básica
Monitoreo endocrino-metabólico y fertilidad: control y optimización del entorno hormonal, perfiles tiroideos, ejes hormonales y marcadores de fertilidad
Interpretación sistémica de analíticas complejas: análisis periódico y avanzado de cribados sanguíneos (perfil lipídico, hepático, renal y hormonal) con recomendaciones específicas
Gestión de farmacocinética y profilaxis: supervisión de la interacción de medicamentos, asimilación de sustancias y control ante patologías previas
Coordinación de diagnóstico por imagen: gestión y análisis de pruebas de imagen médica (ecografías o resonancias) para el control de lesiones o composición corporal interna
Auditoría fisiológica semanal: reporte semanal en lugar de cada 15 días, con ajustes estratégicos de alta frecuencia
Ecosistema de análisis predictivo: plantillas y software profesional para predecir picos de rendimiento y estancamientos
Horario de atención extendido con soporte prioritario: lunes a viernes de 07:00 a 17:00 h y sábados de 07:00 a 14:00 h'
WHERE name = 'Premium';

UPDATE plans SET
    name = 'Básica',
    description = 'Un servicio diseñado para optimizar tu físico, rendimiento y salud mediante un enfoque totalmente personalizado.',
    monthly_price = 117.00,
    quarterly_price = 345.00,
    semiannual_price = 667.00,
    annual_price = 1295.00,
    review_frequency_days = 15,
    messaging_enabled = TRUE,
    analytics_enabled = TRUE,
    pdf_export_enabled = TRUE,
    priority_support = FALSE,
    active = TRUE,
    features = 'Planificación nutricional: diseño dietético individualizado adaptado a tus objetivos y estilo de vida
Programa de entrenamiento: planificación estructurada con progresión de cargas (exclusivo para personas asintomáticas o sin patologías del aparato locomotor activas: óseas, tendinosas o articulares)
Monitoreo en Google Drive: registro sistémico de medidas corporales, cargas y rendimiento
Reajuste estratégico: feedback continuo y modificaciones técnicas según tu evolución
Guía de suplementación: protocolo personalizado según tus necesidades particulares
Control analítico: una (1) analítica sanguínea con interpretación profesional y recomendaciones
Atención al cliente: soporte directo vía WhatsApp de lunes a viernes de 06:00 a 16:00 h'
WHERE name = 'Basic';

-- "Advanced" disappears: its subscribers move to Básica, then the plan is removed.
UPDATE subscriptions
   SET plan_id = (SELECT id FROM plans WHERE name = 'Básica')
 WHERE plan_id IN (SELECT id FROM plans WHERE name = 'Advanced')
   AND EXISTS (SELECT 1 FROM plans WHERE name = 'Básica');
DELETE FROM plans
 WHERE name = 'Advanced'
   AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.plan_id = plans.id);
