import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  NativeModules,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  PluginCommAPI,
  PluginFileAPI,
  PluginManager,
  PluginNoteAPI,
  PointUtils,
} from 'sn-plugin-lib';
import {
  estimateLinkSize,
  findOpenPlacement,
  getPagePlacementArea,
  LINK_FONT_SIZE,
  makeRect,
  pageBottomFromEmrMaxX,
  type PageRect,
  type PageSize,
} from './placement';
import {
  makeLinkLabel,
  normalizeLinkBridgeItem,
  type LinkBridgeItem,
} from './protocol';
import {deliverLink, LinkDeliveryError} from './delivery';

const LINKBRIDGE_SERVICE_UUID = '7e57a001-6e88-4a5c-9f8b-7f9b46351a10';
const LINKBRIDGE_URL_UUID = '7e57a002-6e88-4a5c-9f8b-7f9b46351a10';
const LINKBRIDGE_METADATA_UUID = '7e57a003-6e88-4a5c-9f8b-7f9b46351a10';
const LINKBRIDGE_ACK_UUID = '7e57a004-6e88-4a5c-9f8b-7f9b46351a10';

type ProbeModule = {
  receiveItem(
    serviceUuid: string,
    urlCharacteristicUuid: string,
    metadataCharacteristicUuid: string,
    ackCharacteristicUuid: string,
    timeoutMs: number,
  ): Promise<{
    version: number;
    id: string;
    url: string;
    title: string;
    createdAt: number;
    deviceName?: string;
    deviceAddress?: string;
    rssi: number;
    mtu: number;
    durationMs: number;
  }>;
  hasReceipt(itemId: string): Promise<boolean>;
  recordReceipt(itemId: string): Promise<boolean>;
  acknowledgeItem(itemId: string): Promise<boolean>;
  cancelReceive(): Promise<boolean>;
};

const LinkBridgeProbe = NativeModules.LinkBridgeProbe as ProbeModule | undefined;

function formatError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function getApiResult<T>(response: unknown, operation: string): T {
  const value = response as {
    success?: boolean;
    result?: T;
    error?: {code?: number; message?: string};
  } | null;
  if (!value?.success) {
    const detail = value?.error?.message ?? 'No result returned';
    throw new Error(`${operation} failed: ${detail}`);
  }
  return value.result as T;
}

type PageElement = {
  uuid?: string;
  type?: number;
  pageNum?: number;
  numInPage?: number;
  maxX?: number;
  thickness?: number;
  link?: {
    category?: number;
    X?: number;
    Y?: number;
    width?: number;
    height?: number;
    destPath?: string;
    linkType?: number;
  } | null;
  title?: {X?: number; Y?: number; width?: number; height?: number} | null;
  textBox?: {
    textRect?: {left?: number; top?: number; right?: number; bottom?: number};
  } | null;
  picture?: {
    rect?: {left?: number; top?: number; right?: number; bottom?: number};
  } | null;
  geometry?: {
    points?: Array<{x?: number; y?: number}>;
    ellipseCenterPoint?: {x?: number; y?: number} | null;
    ellipseMajorAxisRadius?: number;
    ellipseMinorAxisRadius?: number;
  } | null;
  fiveStar?: {points?: Array<{x?: number; y?: number}>} | null;
  stroke?: {
    points?: {
      size(): Promise<number>;
      getRange(
        startIndex: number,
        count: number,
      ): Promise<Array<{x?: number; y?: number}>>;
      clearCache?(): void;
    };
  } | null;
};

type Point = {x: number; y: number};

function finiteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function boundsFromPoints(points: Point[]): PageRect | null {
  if (points.length === 0) {
    return null;
  }
  const left = Math.min(...points.map(point => point.x));
  const top = Math.min(...points.map(point => point.y));
  return {
    left,
    top,
    right: Math.max(left + 1, ...points.map(point => point.x)),
    bottom: Math.max(top + 1, ...points.map(point => point.y)),
  };
}

function androidPoint(
  point: {x?: number; y?: number},
  pageSize: PageSize,
): Point | null {
  if (!finiteNumber(point.x) || !finiteNumber(point.y)) {
    return null;
  }
  try {
    return PointUtils.emrPoint2Android(
      {x: Math.round(point.x), y: Math.round(point.y)},
      pageSize,
    );
  } catch {
    return null;
  }
}

function emrRectToAndroid(
  rect: {left?: number; top?: number; right?: number; bottom?: number},
  pageSize: PageSize,
): PageRect | null {
  if (
    !finiteNumber(rect.left) ||
    !finiteNumber(rect.top) ||
    !finiteNumber(rect.right) ||
    !finiteNumber(rect.bottom)
  ) {
    return null;
  }
  const corners = [
    androidPoint({x: rect.left, y: rect.top}, pageSize),
    androidPoint({x: rect.left, y: rect.bottom}, pageSize),
    androidPoint({x: rect.right, y: rect.top}, pageSize),
    androidPoint({x: rect.right, y: rect.bottom}, pageSize),
  ].filter((point): point is Point => point !== null);
  return boundsFromPoints(corners);
}

function getStaticElementBounds(
  element: PageElement,
  pageSize: PageSize,
): PageRect | null {
  const link = element.link;
  if (
    link &&
    finiteNumber(link.X) &&
    finiteNumber(link.Y) &&
    finiteNumber(link.width) &&
    finiteNumber(link.height) &&
    link.width > 0 &&
    link.height > 0
  ) {
    return {
      left: link.X,
      top: link.Y,
      right: link.X + link.width,
      bottom: link.Y + link.height,
    };
  }

  const title = element.title;
  if (
    title &&
    finiteNumber(title.X) &&
    finiteNumber(title.Y) &&
    finiteNumber(title.width) &&
    finiteNumber(title.height) &&
    title.width > 0 &&
    title.height > 0
  ) {
    return {
      left: title.X,
      top: title.Y,
      right: title.X + title.width,
      bottom: title.Y + title.height,
    };
  }

  const textRect = element.textBox?.textRect;
  if (
    textRect &&
    finiteNumber(textRect.left) &&
    finiteNumber(textRect.top) &&
    finiteNumber(textRect.right) &&
    finiteNumber(textRect.bottom) &&
    textRect.right > textRect.left &&
    textRect.bottom > textRect.top
  ) {
    return {
      left: textRect.left,
      top: textRect.top,
      right: textRect.right,
      bottom: textRect.bottom,
    };
  }

  const pictureRect = element.picture?.rect;
  if (pictureRect) {
    return emrRectToAndroid(pictureRect, pageSize);
  }

  const geometryPoints = (element.geometry?.points ?? []).filter(
    (point): point is Point =>
      finiteNumber(point.x) && finiteNumber(point.y),
  );
  if (geometryPoints.length > 0) {
    return boundsFromPoints(geometryPoints);
  }

  const ellipse = element.geometry?.ellipseCenterPoint;
  if (ellipse && finiteNumber(ellipse.x) && finiteNumber(ellipse.y)) {
    const radius = Math.max(
      element.geometry?.ellipseMajorAxisRadius ?? 0,
      element.geometry?.ellipseMinorAxisRadius ?? 0,
    );
    return {
      left: ellipse.x - radius,
      top: ellipse.y - radius,
      right: ellipse.x + radius,
      bottom: ellipse.y + radius,
    };
  }

  const starPoints = (element.fiveStar?.points ?? [])
    .map(point => androidPoint(point, pageSize))
    .filter((point): point is Point => point !== null);
  if (starPoints.length > 0) {
    return boundsFromPoints(starPoints);
  }

  return null;
}

function getQuickElementBottom(
  element: PageElement,
  pageSize: PageSize,
): number | null {
  const staticBounds = getStaticElementBounds(element, pageSize);
  if (staticBounds) {
    return staticBounds.bottom;
  }

  // Handwriting uses EMR pen coordinates. EMR X maps to the page's vertical
  // screen axis, so maxX—not maxY—is the reliable bottom edge.
  if (finiteNumber(element.maxX) && element.maxX >= 0) {
    return pageBottomFromEmrMaxX(element.maxX, pageSize, androidPoint);
  }

  return null;
}

async function getElementBounds(
  element: PageElement,
  pageSize: PageSize,
): Promise<PageRect> {
  const staticBounds = getStaticElementBounds(element, pageSize);
  if (staticBounds) {
    return staticBounds;
  }

  const strokePoints = element.stroke?.points;
  if (strokePoints) {
    try {
      const count = await strokePoints.size();
      const converted: Point[] = [];
      for (let index = 0; index < count; index += 512) {
        const points = await strokePoints.getRange(
          index,
          Math.min(512, count - index),
        );
        for (const point of points) {
          const convertedPoint = androidPoint(point, pageSize);
          if (convertedPoint) {
            converted.push(convertedPoint);
          }
        }
      }
      const bounds = boundsFromPoints(converted);
      if (bounds) {
        return bounds;
      }
    } catch {
      // Fall through to the conservative element-level boundary below.
    } finally {
      strokePoints.clearCache?.();
    }
  }

  const bottom = getQuickElementBottom(element, pageSize);
  if (bottom !== null) {
    return {left: 0, top: 0, right: pageSize.width, bottom};
  }

  // A future element the SDK cannot describe is treated conservatively.
  return {left: 0, top: 0, right: pageSize.width, bottom: pageSize.height};
}

async function insertItem(item: LinkBridgeItem): Promise<number> {
  const {url} = item;
  const label = makeLinkLabel(item.title, url);
  const filePath = getApiResult<string>(
    await PluginCommAPI.getCurrentFilePath(),
    'Get current file',
  );
  const page = getApiResult<number>(
    await PluginCommAPI.getCurrentPageNum(),
    'Get current page',
  );
  const pageSize = getApiResult<{width: number; height: number}>(
    await PluginFileAPI.getPageSize(filePath, page),
    'Get page size',
  );
  const elements = getApiResult<PageElement[]>(
    await PluginFileAPI.getElements(page, filePath),
    'Read current page layout',
  );

  const linkSize = estimateLinkSize(label, pageSize);
  const placementArea = getPagePlacementArea(pageSize);
  const quickBottoms = elements.map(element =>
    getQuickElementBottom(element, pageSize),
  );
  const allElementsMeasured = quickBottoms.every(
    (bottom): bottom is number => bottom !== null,
  );
  const occupiedBottom = Math.max(
    0,
    ...quickBottoms.filter((bottom): bottom is number => bottom !== null),
  );
  const preferredTop = Math.max(placementArea.top, occupiedBottom + 18);

  let linkRect: PageRect | null = null;
  if (
    allElementsMeasured &&
    preferredTop + linkSize.height <= placementArea.bottom
  ) {
    linkRect = makeRect(placementArea.left, preferredTop, linkSize);
  } else {
    const elementBounds = await Promise.all(
      elements.map(element => getElementBounds(element, pageSize)),
    );
    linkRect = findOpenPlacement(
      pageSize,
      linkSize,
      elementBounds,
      preferredTop,
    );
  }

  if (!linkRect) {
    throw new Error('No clear spot on this page is large enough for the link.');
  }

  const insertResult = await PluginNoteAPI.insertTextLink({
    destPath: url,
    destPage: 0,
    style: 1,
    linkType: 4,
    rect: linkRect,
    fontSize: LINK_FONT_SIZE,
    fullText: label,
    showText: label,
    isItalic: 0,
  });
  getApiResult<number>(insertResult, 'Insert URL link');
  getApiResult<boolean>(
    await PluginNoteAPI.saveCurrentNote(),
    'Save current note',
  );

  try {
    const refreshedElements = getApiResult<PageElement[]>(
      await PluginFileAPI.getElements(page, filePath),
      'Read inserted link',
    );
    const insertedLink = refreshedElements
      .filter(
        element =>
          (element.link?.category ?? 0) === 0 &&
          element.link?.linkType === 4 &&
          element.link?.destPath === url,
      )
      .sort((a, b) => (b.numInPage ?? -1) - (a.numInPage ?? -1))[0]?.link;
    const lassoRect = insertedLink
      ? {
          left: Math.max(0, (insertedLink.X ?? linkRect.left) - 8),
          top: Math.max(0, (insertedLink.Y ?? linkRect.top) - 8),
          right: Math.min(
            pageSize.width,
            (insertedLink.X ?? linkRect.left) +
              (insertedLink.width ?? linkSize.width) +
              8,
          ),
          bottom: Math.min(
            pageSize.height,
            (insertedLink.Y ?? linkRect.top) +
              (insertedLink.height ?? linkSize.height) +
              8,
          ),
        }
      : linkRect;
    getApiResult<boolean>(
      await PluginCommAPI.lassoElements(lassoRect),
      'Select inserted link',
    );
    getApiResult<boolean>(
      await PluginCommAPI.setLassoBoxState(0),
      'Show link selection',
    );
  } catch (error) {
    // The URL is already safely stored. Do not turn a selection failure into
    // a retry that could insert a duplicate link.
    console.warn('LinkBridge could not select the inserted link', error);
  }
  return page;
}

function App(): React.JSX.Element {
  const [receiveStatus, setReceiveStatus] = useState('Receiving link…');
  const [resultTitle, setResultTitle] = useState('Receiving…');
  const [busy, setBusy] = useState(true);
  const startedAutomatically = useRef(false);

  const receiveAndInsertUrl = useCallback(async () => {
    if (!LinkBridgeProbe) {
      setReceiveStatus('LinkBridge Bluetooth support could not be loaded.');
      setBusy(false);
      return;
    }
    setBusy(true);
    setResultTitle('Receiving…');
    setReceiveStatus('Receiving link…');
    let sessionOpen = false;
    try {
      const received = await LinkBridgeProbe.receiveItem(
        LINKBRIDGE_SERVICE_UUID,
        LINKBRIDGE_URL_UUID,
        LINKBRIDGE_METADATA_UUID,
        LINKBRIDGE_ACK_UUID,
        15000,
      );
      sessionOpen = true;
      const item = normalizeLinkBridgeItem(received);
      const delivery = await deliverLink(item, {
        hasReceipt: itemId => LinkBridgeProbe.hasReceipt(itemId),
        insert: async linkItem => {
          await insertItem(linkItem);
        },
        recordReceipt: itemId => LinkBridgeProbe.recordReceipt(itemId),
        acknowledge: itemId => LinkBridgeProbe.acknowledgeItem(itemId),
      });
      sessionOpen = false;
      setResultTitle('Saved');
      setReceiveStatus(
        delivery.inserted
          ? 'Link added, saved, and selected.'
          : 'This retry was already saved. The phone outbox is now clear.',
      );
      const closed = await PluginManager.closePluginView();
      if (!closed) {
        setReceiveStatus('Link saved. Close this panel to continue.');
      }
    } catch (error) {
      if (sessionOpen) {
        try {
          await LinkBridgeProbe.cancelReceive();
        } catch {
          // Preserve the original, more useful failure below.
        }
      }
      if (error instanceof LinkDeliveryError && error.linkSaved) {
        setResultTitle('Link saved');
        setReceiveStatus(
          `The note is safe, but the phone did not receive confirmation. Reopen LinkBridge to clear the queued retry. ${formatError(
            error,
          )}`,
        );
      } else {
        setResultTitle('Could not add the link');
        setReceiveStatus(formatError(error));
      }
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    if (!startedAutomatically.current) {
      startedAutomatically.current = true;
      receiveAndInsertUrl();
    }
  }, [receiveAndInsertUrl]);

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>LinkBridge</Text>
          <Text style={styles.subtitle}>Phone → note</Text>
        </View>
        <Pressable
          accessibilityRole="button"
          style={styles.closeButton}
          onPress={() => PluginManager.closePluginView()}>
          <Text style={styles.closeText}>Close</Text>
        </Pressable>
      </View>

      <View style={styles.content}>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>
            {busy ? 'Receiving…' : resultTitle}
          </Text>
          <Text style={styles.result}>{receiveStatus}</Text>
          {!busy && (
            <Pressable
              accessibilityRole="button"
              onPress={receiveAndInsertUrl}
              style={styles.action}>
              <Text style={styles.actionText}>Retry</Text>
            </Pressable>
          )}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 16,
    borderBottomWidth: 2,
    borderBottomColor: '#111111',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    color: '#000000',
    fontSize: 28,
    fontWeight: '700',
  },
  subtitle: {
    color: '#444444',
    fontSize: 16,
    marginTop: 2,
  },
  closeButton: {
    borderWidth: 2,
    borderColor: '#111111',
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  closeText: {
    color: '#000000',
    fontSize: 17,
    fontWeight: '600',
  },
  content: {
    flex: 1,
    padding: 20,
  },
  card: {
    borderWidth: 2,
    borderColor: '#111111',
    padding: 24,
  },
  cardTitle: {
    color: '#000000',
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 12,
  },
  action: {
    alignSelf: 'flex-start',
    backgroundColor: '#111111',
    paddingHorizontal: 18,
    paddingVertical: 12,
    marginBottom: 12,
  },
  actionText: {
    color: '#ffffff',
    fontSize: 17,
    fontWeight: '700',
  },
  result: {
    color: '#111111',
    fontSize: 17,
    lineHeight: 25,
    marginBottom: 20,
  },
});

export default App;
