import {
  LINKBRIDGE_PROTOCOL_VERSION,
  makeLinkLabel,
  normalizeLinkBridgeItem,
} from './protocol';

describe('LinkBridge protocol', () => {
  it('preserves the exact URL while normalizing the title', () => {
    const url = 'https://example.com/Case?escaped=%2B%26%3D#Frag';
    expect(
      normalizeLinkBridgeItem({
        version: 1,
        id: 'item-1',
        url,
        title: '  A   title about Saïx  ',
        createdAt: 1234,
      }),
    ).toEqual({
      version: LINKBRIDGE_PROTOCOL_VERSION,
      id: 'item-1',
      url,
      title: 'A title about Saïx',
      createdAt: 1234,
    });
  });

  it('rejects incompatible or malformed messages', () => {
    expect(() =>
      normalizeLinkBridgeItem({version: 2, id: 'x', url: 'https://example.com'}),
    ).toThrow('unsupported');
    expect(() =>
      normalizeLinkBridgeItem({version: 1, id: '', url: 'https://example.com'}),
    ).toThrow('item ID');
    expect(() =>
      normalizeLinkBridgeItem({version: 1, id: 'x', url: 'not a URL'}),
    ).toThrow('web address');
  });

  it('uses a human title without changing the destination URL', () => {
    expect(makeLinkLabel('Why VA Spending Matters', 'https://example.com/opaque')).toBe(
      'Why VA Spending Matters',
    );
  });

  it('falls back to the hostname and truncates by Unicode code point', () => {
    expect(makeLinkLabel('', 'https://www.example.com/article')).toBe('example.com');
    expect(makeLinkLabel('Saïx and friends', 'https://example.com', 8)).toBe('Saïx an…');
  });
});
