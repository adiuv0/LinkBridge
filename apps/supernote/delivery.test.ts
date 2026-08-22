import {deliverLink, LinkDeliveryError} from './delivery';
import type {LinkBridgeItem} from './protocol';

const item: LinkBridgeItem = {
  version: 1,
  id: 'item-1',
  url: 'https://example.com/article',
  title: 'Example article',
  createdAt: 1234,
};

describe('save-confirmed delivery', () => {
  it('inserts, records a durable receipt, then acknowledges in that order', async () => {
    const calls: string[] = [];
    const result = await deliverLink(item, {
      hasReceipt: async () => {
        calls.push('hasReceipt');
        return false;
      },
      insert: async () => {
        calls.push('insertAndSave');
      },
      recordReceipt: async () => {
        calls.push('recordReceipt');
        return true;
      },
      acknowledge: async () => {
        calls.push('acknowledge');
        return true;
      },
    });

    expect(result).toEqual({inserted: true});
    expect(calls).toEqual([
      'hasReceipt',
      'insertAndSave',
      'recordReceipt',
      'acknowledge',
    ]);
  });

  it('acknowledges a retry without inserting a duplicate', async () => {
    const insert = jest.fn(async () => undefined);
    const recordReceipt = jest.fn(async () => true);
    const acknowledge = jest.fn(async () => true);

    const result = await deliverLink(item, {
      hasReceipt: async () => true,
      insert,
      recordReceipt,
      acknowledge,
    });

    expect(result).toEqual({inserted: false});
    expect(insert).not.toHaveBeenCalled();
    expect(recordReceipt).not.toHaveBeenCalled();
    expect(acknowledge).toHaveBeenCalledWith(item.id);
  });

  it('never acknowledges an insertion failure', async () => {
    const acknowledge = jest.fn(async () => true);
    await expect(
      deliverLink(item, {
        hasReceipt: async () => false,
        insert: async () => {
          throw new Error('save failed');
        },
        recordReceipt: async () => true,
        acknowledge,
      }),
    ).rejects.toMatchObject({linkSaved: false});
    expect(acknowledge).not.toHaveBeenCalled();
  });

  it('marks a lost acknowledgement as saved so a receipt-backed retry is safe', async () => {
    let receiptExists = false;
    const insert = jest.fn(async () => undefined);
    const dependencies = {
      hasReceipt: async () => receiptExists,
      insert,
      recordReceipt: async () => {
        receiptExists = true;
        return true;
      },
      acknowledge: jest.fn(async () => false),
    };

    try {
      await deliverLink(item, dependencies);
      throw new Error('Expected delivery to fail');
    } catch (error) {
      expect(error).toBeInstanceOf(LinkDeliveryError);
      expect((error as LinkDeliveryError).linkSaved).toBe(true);
    }
    dependencies.acknowledge.mockResolvedValueOnce(true);
    await expect(deliverLink(item, dependencies)).resolves.toEqual({inserted: false});
    expect(insert).toHaveBeenCalledTimes(1);
  });
});
