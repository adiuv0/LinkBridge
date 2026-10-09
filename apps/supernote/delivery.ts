import type {LinkBridgeItem} from './protocol';

export type DeliveryDependencies = {
  hasReceipt(itemId: string): Promise<boolean>;
  insert(item: LinkBridgeItem): Promise<void>;
  recordReceipt(itemId: string): Promise<boolean>;
  acknowledge(itemId: string): Promise<boolean>;
};

export type DeliveryResult = {
  inserted: boolean;
};

export class LinkDeliveryError extends Error {
  readonly linkSaved: boolean;
  readonly originalError?: unknown;

  constructor(message: string, linkSaved: boolean, cause?: unknown) {
    super(message);
    this.name = 'LinkDeliveryError';
    this.linkSaved = linkSaved;
    this.originalError = cause;
  }
}

export async function deliverLink(
  item: LinkBridgeItem,
  dependencies: DeliveryDependencies,
): Promise<DeliveryResult> {
  let linkSaved = false;
  try {
    const alreadyInserted = await dependencies.hasReceipt(item.id);
    if (!alreadyInserted) {
      await dependencies.insert(item);
      linkSaved = true;
      if (!(await dependencies.recordReceipt(item.id))) {
        throw new Error('The duplicate-protection receipt could not be saved.');
      }
    } else {
      linkSaved = true;
    }
    if (!(await dependencies.acknowledge(item.id))) {
      throw new Error('The phone did not accept the save confirmation.');
    }
    return {inserted: !alreadyInserted};
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new LinkDeliveryError(message, linkSaved, error);
  }
}

export type DrainProgress = {
  delivered: number;
  inserted: number;
};

/**
 * Receives and delivers links until the phone reports none remaining or the
 * per-open limit is reached. `receiveAndDeliver` handles one link and returns
 * whether it was newly inserted and how many links the phone still holds.
 */
export async function drainQueue(
  receiveAndDeliver: (
    index: number,
    remaining: number,
  ) => Promise<{inserted: boolean; remaining: number}>,
  maxItems: number,
  progress: DrainProgress = {delivered: 0, inserted: 0},
): Promise<DrainProgress> {
  let remaining = 0;
  do {
    const result = await receiveAndDeliver(progress.delivered, remaining);
    progress.delivered += 1;
    if (result.inserted) {
      progress.inserted += 1;
    }
    remaining = Math.max(0, Math.floor(result.remaining));
  } while (remaining > 0 && progress.delivered < maxItems);
  return progress;
}
