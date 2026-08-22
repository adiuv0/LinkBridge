import {
  estimateLinkSize,
  findOpenPlacement,
  getPagePlacementArea,
  pageBottomFromEmrMaxX,
  type PageRect,
} from './placement';

const page = {width: 1404, height: 1872};

describe('link placement', () => {
  it('uses EMR X as the handwriting vertical axis', () => {
    const convert = jest.fn(() => ({x: 200, y: 640}));

    expect(pageBottomFromEmrMaxX(4321, page, convert)).toBe(640);
    expect(convert).toHaveBeenCalledWith({x: 4321, y: 0}, page);
  });

  it('reserves approximately the native rendered link height', () => {
    expect(estimateLinkSize('https://example.com', page).height).toBe(48);
  });

  it('uses the preferred clear position', () => {
    const result = findOpenPlacement(
      page,
      estimateLinkSize('https://example.com', page),
      [{left: 100, top: 100, right: 800, bottom: 500}],
      524,
    );

    expect(result?.top).toBe(524);
    expect(result?.left).toBe(getPagePlacementArea(page).left);
  });

  it('finds a real gap when content reaches the bottom of the page', () => {
    const obstacles: PageRect[] = [
      {left: 90, top: 140, right: 1300, bottom: 400},
      {left: 90, top: 600, right: 1300, bottom: 1800},
    ];
    const result = findOpenPlacement(
      page,
      {width: 1000, height: 48},
      obstacles,
      1650,
    );

    expect(result).not.toBeNull();
    expect(result!.top).toBeGreaterThanOrEqual(412);
    expect(result!.bottom).toBeLessThanOrEqual(588);
  });

  it('returns null only when no link-sized opening remains', () => {
    const area = getPagePlacementArea(page);
    const result = findOpenPlacement(
      page,
      {width: area.right - area.left, height: 48},
      [area],
      area.bottom - 48,
    );

    expect(result).toBeNull();
  });
});
