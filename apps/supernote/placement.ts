export type PageSize = {
  width: number;
  height: number;
};

export type PageRect = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

export type LinkSize = {
  width: number;
  height: number;
};

export type PagePoint = {
  x: number;
  y: number;
};

export const LINK_FONT_SIZE = 24;

/**
 * Stroke extrema are stored in EMR pen coordinates. EMR X is the page's
 * vertical axis, so the maximum X must be converted as a point's X value.
 */
export function pageBottomFromEmrMaxX(
  maxX: number,
  pageSize: PageSize,
  toPagePoint: (point: PagePoint, size: PageSize) => PagePoint | null,
): number | null {
  const point = toPagePoint({x: maxX, y: 0}, pageSize);
  return point?.y ?? null;
}

export function estimateLinkSize(
  url: string,
  pageSize: PageSize,
  fontSize = LINK_FONT_SIZE,
): LinkSize {
  const sideMargin = Math.max(24, Math.round(pageSize.width * 0.08));
  const availableWidth = pageSize.width - sideMargin * 2;
  const estimatedTextWidth = Math.ceil(
    url.length * fontSize * 0.46 + fontSize * 2,
  );

  return {
    width: Math.min(
      availableWidth,
      Math.max(Math.round(pageSize.width * 0.22), estimatedTextWidth),
    ),
    // Native text links at 24 px render at roughly 43 px high on the Nomad.
    // Keep a little breathing room without reserving the old 100+ px band.
    height: Math.ceil(fontSize * 2),
  };
}

export function getPagePlacementArea(pageSize: PageSize): PageRect {
  const sideMargin = Math.max(24, Math.round(pageSize.width * 0.08));
  return {
    left: sideMargin,
    top: Math.max(24, Math.round(pageSize.height * 0.08)),
    right: pageSize.width - sideMargin,
    // Keep clear of the note application's page controls at the bottom.
    bottom:
      pageSize.height - Math.max(72, Math.round(pageSize.height * 0.075)),
  };
}

export function makeRect(
  left: number,
  top: number,
  size: LinkSize,
): PageRect {
  return {
    left,
    top,
    right: left + size.width,
    bottom: top + size.height,
  };
}

function normalizeRect(rect: PageRect): PageRect | null {
  const left = Math.min(rect.left, rect.right);
  const top = Math.min(rect.top, rect.bottom);
  const right = Math.max(rect.left, rect.right);
  const bottom = Math.max(rect.top, rect.bottom);
  if (
    ![left, top, right, bottom].every(Number.isFinite) ||
    right <= left ||
    bottom <= top
  ) {
    return null;
  }
  return {left, top, right, bottom};
}

function intersects(a: PageRect, b: PageRect, padding: number): boolean {
  return !(
    a.right <= b.left - padding ||
    a.left >= b.right + padding ||
    a.bottom <= b.top - padding ||
    a.top >= b.bottom + padding
  );
}

function uniquePositions(values: number[]): number[] {
  return [...new Set(values.filter(Number.isFinite).map(Math.round))];
}

/**
 * Finds a clear link-sized rectangle. Candidates sit on real element edges,
 * so this can use narrow gaps without an expensive pixel-by-pixel scan.
 */
export function findOpenPlacement(
  pageSize: PageSize,
  linkSize: LinkSize,
  rawObstacles: PageRect[],
  preferredTop: number,
  padding = 12,
): PageRect | null {
  const area = getPagePlacementArea(pageSize);
  const width = Math.min(linkSize.width, area.right - area.left);
  const height = linkSize.height;
  const maxLeft = area.right - width;
  const maxTop = area.bottom - height;
  if (maxLeft < area.left || maxTop < area.top) {
    return null;
  }

  const obstacles = rawObstacles
    .map(normalizeRect)
    .filter((rect): rect is PageRect => rect !== null)
    .filter(
      rect =>
        rect.right > area.left &&
        rect.left < area.right &&
        rect.bottom > area.top &&
        rect.top < area.bottom,
    );

  const preferred = Math.max(area.top, Math.min(preferredTop, maxTop));
  const xs = uniquePositions([
    area.left,
    maxLeft,
    ...obstacles.flatMap(rect => [
      rect.right + padding,
      rect.left - width - padding,
    ]),
  ]).filter(left => left >= area.left && left <= maxLeft);
  const ys = uniquePositions([
    preferred,
    area.top,
    maxTop,
    ...obstacles.flatMap(rect => [
      rect.bottom + padding,
      rect.top - height - padding,
    ]),
  ]).filter(top => top >= area.top && top <= maxTop);

  const candidates = ys.flatMap(top =>
    xs.map(left => makeRect(left, top, {width, height})),
  );
  candidates.sort((a, b) => {
    const vertical =
      Math.abs(a.top - preferred) - Math.abs(b.top - preferred);
    if (vertical !== 0) {
      return vertical;
    }
    return a.left - b.left;
  });

  return (
    candidates.find(candidate =>
      obstacles.every(obstacle => !intersects(candidate, obstacle, padding)),
    ) ?? null
  );
}
