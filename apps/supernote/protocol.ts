export const LINKBRIDGE_PROTOCOL_VERSION = 1;

export type LinkBridgeItem = {
  version: number;
  id: string;
  url: string;
  title: string;
  createdAt: number;
};

type WireItem = {
  version?: unknown;
  id?: unknown;
  url?: unknown;
  title?: unknown;
  createdAt?: unknown;
};

function isHttpUrl(value: string): boolean {
  return value.startsWith('https://') || value.startsWith('http://');
}

export function normalizeLinkBridgeItem(value: unknown): LinkBridgeItem {
  const item = (value ?? {}) as WireItem;
  if (item.version !== LINKBRIDGE_PROTOCOL_VERSION) {
    throw new Error('The phone is using an unsupported LinkBridge version.');
  }
  if (typeof item.id !== 'string' || item.id.trim().length === 0) {
    throw new Error('The phone sent a link without an item ID.');
  }
  if (typeof item.url !== 'string' || !isHttpUrl(item.url.trim())) {
    throw new Error('The phone sent text that is not a web address.');
  }
  return {
    version: LINKBRIDGE_PROTOCOL_VERSION,
    id: item.id.trim(),
    url: item.url.trim(),
    title: typeof item.title === 'string' ? item.title.replace(/\s+/g, ' ').trim() : '',
    createdAt:
      typeof item.createdAt === 'number' && Number.isFinite(item.createdAt)
        ? item.createdAt
        : 0,
  };
}

export function makeLinkLabel(
  title: string,
  url: string,
  maxCodePoints = 120,
): string {
  let label = title.replace(/\s+/g, ' ').trim();
  if (!label || label === url) {
    try {
      label = new URL(url).hostname.replace(/^www\./i, '');
    } catch {
      label = 'Web link';
    }
  }
  const characters = Array.from(label);
  if (characters.length <= maxCodePoints) {
    return label;
  }
  return `${characters.slice(0, Math.max(1, maxCodePoints - 1)).join('').trim()}…`;
}
