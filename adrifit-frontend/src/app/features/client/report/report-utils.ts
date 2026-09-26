import { WeeklyReport } from '../../../shared/models/report.model';

/** Parses a decimal typed by the user ("79,5", "79.5", " 80 "). Returns null when it is not a number. */
export function parseDecimal(value: string | number | null | undefined): number | null {
  if (value == null) return null;
  const text = String(value).trim().replace(',', '.');
  if (!text || !/^\d+(\.\d+)?$/.test(text)) return null;
  const n = Number(text);
  return Number.isFinite(n) ? n : null;
}

/**
 * Weight change of every report vs the previous report of the same client (by date).
 * Works with lists that mix several clients (trainer view). Map key = report id.
 */
export function weightDeltas(reports: WeeklyReport[]): Map<number, number> {
  const byClient = new Map<number, WeeklyReport[]>();
  for (const r of reports) {
    if (!byClient.has(r.clientId)) byClient.set(r.clientId, []);
    byClient.get(r.clientId)!.push(r);
  }
  const out = new Map<number, number>();
  for (const list of byClient.values()) {
    const asc = [...list].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id);
    for (let i = 1; i < asc.length; i++) {
      if (asc[i].weight != null && asc[i - 1].weight != null) {
        out.set(asc[i].id, Math.round((asc[i].weight - asc[i - 1].weight) * 10) / 10);
      }
    }
  }
  return out;
}

/** True when an old report carries any of the legacy metrics (waist, fat, energy, adherence). */
export function hasLegacyMetrics(r: WeeklyReport): boolean {
  return r.waist != null || r.bodyFat != null || r.energyLevel != null || r.dietAdherence != null || r.trainingAdherence != null;
}
