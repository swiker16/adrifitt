import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { AuthResponse } from '../../shared/models/auth.model';
import { API_BASE_URL } from '../config/api.config';

export interface Passkey {
  id: number;
  name: string;
  /** Synced across the user's devices (iCloud Keychain, Google Password Manager...). */
  synced: boolean;
  createdAt: string;
  lastUsedAt: string | null;
}

interface CeremonyOptions {
  requestId: string;
  /** Standard WebAuthn options JSON (binary fields base64url). */
  publicKey: any;
}

/**
 * Passkeys (WebAuthn): Face ID / Touch ID, Android fingerprint, Windows Hello...
 * Converts between the JSON used by the API and the ArrayBuffers the browser expects.
 */
@Injectable({ providedIn: 'root' })
export class PasskeyService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;
  private conditionalAbort: AbortController | null = null;

  /** WebAuthn available in this browser. */
  isSupported(): boolean {
    return typeof window !== 'undefined' && !!window.PublicKeyCredential && !!navigator.credentials;
  }

  /** The device has a built-in biometric/PIN authenticator (Face ID, fingerprint, Hello). */
  async hasPlatformAuthenticator(): Promise<boolean> {
    if (!this.isSupported()) return false;
    try {
      return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    } catch {
      return false;
    }
  }

  /** Friendly name of the local biometric method for UI texts. */
  biometricLabel(): string {
    const ua = navigator.userAgent;
    if (/iPhone|iPad/.test(ua)) return 'Face ID o Touch ID';
    if (/Macintosh/.test(ua)) return 'Touch ID';
    if (/Android/.test(ua)) return 'tu huella o reconocimiento facial';
    if (/Windows/.test(ua)) return 'Windows Hello';
    return 'la biometría de tu dispositivo';
  }

  list(): Observable<Passkey[]> {
    return this.http.get<Passkey[]>(`${this.base}/passkeys`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/passkeys/${id}`);
  }

  /** Creates a passkey for the logged-in user (shows the system Face ID / fingerprint sheet). */
  async register(name?: string): Promise<Passkey> {
    this.abortConditional();
    const options = await firstValueFrom(this.http.post<CeremonyOptions>(`${this.base}/passkeys/register/options`, {}));
    const pk = options.publicKey;
    const publicKey: PublicKeyCredentialCreationOptions = {
      ...pk,
      challenge: toBuffer(pk.challenge),
      user: { ...pk.user, id: toBuffer(pk.user.id) },
      excludeCredentials: (pk.excludeCredentials ?? []).map((c: any) => ({ ...c, id: toBuffer(c.id) })),
    };
    stripNulls(publicKey);
    const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential | null;
    if (!credential) throw new Error('cancelled');
    const response = credential.response as AuthenticatorAttestationResponse;
    const json = {
      id: credential.id,
      rawId: toBase64Url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: toBase64Url(response.clientDataJSON),
        attestationObject: toBase64Url(response.attestationObject),
        transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
      },
      clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
      ...(credential.authenticatorAttachment ? { authenticatorAttachment: credential.authenticatorAttachment } : {}),
    };
    return firstValueFrom(
      this.http.post<Passkey>(`${this.base}/passkeys/register/finish`, {
        requestId: options.requestId,
        credential: json,
        name: name || null,
      })
    );
  }

  /**
   * Signs in with a passkey. {@code conditional} = passkey autofill in the username field
   * (resolves only if the user picks a passkey from the suggestions).
   */
  async signIn(conditional = false): Promise<AuthResponse> {
    this.abortConditional();
    const options = await firstValueFrom(this.http.post<CeremonyOptions>(`${this.base}/auth/passkey/options`, {}));
    const pk = options.publicKey;
    const publicKey: PublicKeyCredentialRequestOptions = {
      ...pk,
      challenge: toBuffer(pk.challenge),
      allowCredentials: (pk.allowCredentials ?? []).map((c: any) => ({ ...c, id: toBuffer(c.id) })),
    };
    stripNulls(publicKey);
    const request: CredentialRequestOptions = { publicKey };
    if (conditional) {
      this.conditionalAbort = new AbortController();
      request.signal = this.conditionalAbort.signal;
      (request as any).mediation = 'conditional';
    }
    const credential = (await navigator.credentials.get(request)) as PublicKeyCredential | null;
    if (!credential) throw new Error('cancelled');
    const response = credential.response as AuthenticatorAssertionResponse;
    const json = {
      id: credential.id,
      rawId: toBase64Url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: toBase64Url(response.clientDataJSON),
        authenticatorData: toBase64Url(response.authenticatorData),
        signature: toBase64Url(response.signature),
        userHandle: response.userHandle ? toBase64Url(response.userHandle) : null,
      },
      clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
      ...(credential.authenticatorAttachment ? { authenticatorAttachment: credential.authenticatorAttachment } : {}),
    };
    return firstValueFrom(
      this.http.post<AuthResponse>(`${this.base}/auth/passkey/finish`, { requestId: options.requestId, credential: json })
    );
  }

  async conditionalMediationAvailable(): Promise<boolean> {
    try {
      return this.isSupported() && !!(PublicKeyCredential as any).isConditionalMediationAvailable
        && (await (PublicKeyCredential as any).isConditionalMediationAvailable());
    } catch {
      return false;
    }
  }

  abortConditional(): void {
    if (this.conditionalAbort) {
      this.conditionalAbort.abort();
      this.conditionalAbort = null;
    }
  }
}

/** True when the user closed/cancelled the system sheet (not a real error). */
export function isPasskeyCancel(err: unknown): boolean {
  const name = (err as { name?: string })?.name;
  return name === 'NotAllowedError' || name === 'AbortError' || (err as Error)?.message === 'cancelled';
}

function toBuffer(value: string): ArrayBuffer {
  const b64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
  const bin = atob(padded);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}

function toBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Some browsers reject explicit nulls in WebAuthn dictionaries. */
function stripNulls(obj: any): void {
  for (const key of Object.keys(obj)) {
    if (obj[key] === null) delete obj[key];
    else if (typeof obj[key] === 'object' && !(obj[key] instanceof ArrayBuffer) && !Array.isArray(obj[key])) stripNulls(obj[key]);
  }
}
