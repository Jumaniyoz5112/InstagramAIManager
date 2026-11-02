import { I18nService } from './i18n.service';

interface ApiErrorBody {
  messageKey?: string;
  messageArgs?: string[];
  message?: string;
}

export function translateApiError(
  i18n: I18nService,
  err: unknown,
  fallbackKey = 'error.generic'
): string {
  const body = extractBody(err);
  const key = body?.messageKey || body?.message;
  if (key && key.startsWith('error.')) {
    return i18n.t(key, body?.messageArgs ?? []);
  }
  if (key && i18n.t(key) !== key) {
    return i18n.t(key, body?.messageArgs ?? []);
  }
  return i18n.t(fallbackKey);
}

function extractBody(err: unknown): ApiErrorBody | null {
  if (typeof err !== 'object' || err === null || !('error' in err)) {
    return null;
  }
  const nested = (err as { error?: unknown }).error;
  if (typeof nested !== 'object' || nested === null) {
    return null;
  }
  return nested as ApiErrorBody;
}
