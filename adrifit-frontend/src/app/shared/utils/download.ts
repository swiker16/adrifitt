/** Saves a Blob received from the API (PDFs, documents) as a file. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Opens a Blob in a new tab (e.g. inline PDF preview). */
export function openBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

/** Human readable message from an HttpErrorResponse coming from the API. */
export function apiErrorMessage(err: unknown, fallback = 'Ha ocurrido un error. Inténtalo de nuevo.'): string {
  const e = err as { status?: number; error?: { message?: string; fieldErrors?: Record<string, string> } };
  if (e?.status === 0) return 'No hay conexión con el servidor.';
  const fieldErrors = e?.error?.fieldErrors;
  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    return Object.values(fieldErrors)[0];
  }
  const message = e?.error?.message;
  if (message && message !== 'Unexpected error' && message !== 'Access denied' && message !== 'Validation failed') {
    return message;
  }
  return fallback;
}

/** yyyy-MM-dd of a Date in local time (for <input type="date"> and API LocalDate params). */
export function isoDate(d: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
