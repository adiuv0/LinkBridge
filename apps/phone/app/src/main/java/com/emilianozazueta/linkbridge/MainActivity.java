package com.emilianozazueta.linkbridge;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 701;
    private static final String STATE_ACTIVE_ITEM_ID = "activeItemId";
    private static final Pattern HTTP_URL = Pattern.compile("https?://\\S+");

    public static final UUID SERVICE_UUID = UUID.fromString("7e57a001-6e88-4a5c-9f8b-7f9b46351a10");
    public static final UUID URL_UUID = UUID.fromString("7e57a002-6e88-4a5c-9f8b-7f9b46351a10");
    public static final UUID METADATA_UUID = UUID.fromString("7e57a003-6e88-4a5c-9f8b-7f9b46351a10");
    public static final UUID ACK_UUID = UUID.fromString("7e57a004-6e88-4a5c-9f8b-7f9b46351a10");

    private EditText titleInput;
    private EditText urlInput;
    private TextView status;
    private TextView outboxStatus;
    private BluetoothAdapter adapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private LinkOutbox outbox;
    private volatile LinkItem activeItem;
    // The Supernote that read the active URL. Only it may confirm the save.
    private volatile String servedDeviceAddress;
    // Metadata is fixed per send so every part of a long Bluetooth read matches.
    private volatile byte[] activeMetadata;
    private LinkItem pendingPermissionItem;
    private boolean advertising;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        outbox = new LinkOutbox(this);
        buildUi();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        showLatestItem();

        // A share intent is handled once. Activity recreation (for example after
        // a configuration change) and relaunching from Recents replay the
        // original intent, which would otherwise queue the same link again under
        // a new ID that the Supernote's duplicate check cannot recognize.
        boolean launchedFromHistory =
            (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
        if (state == null && !launchedFromHistory) {
            handleIntent(getIntent());
        } else if (state != null) {
            String activeId = state.getString(STATE_ACTIVE_ITEM_ID);
            LinkItem resumed = activeId == null ? null : outbox.find(activeId);
            if (resumed != null) {
                showItem(resumed);
                startSending(resumed);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        LinkItem item = activeItem;
        if (item != null) state.putString(STATE_ACTIVE_ITEM_ID, item.id);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(this);
        heading.setText("LinkBridge");
        heading.setTextSize(30);
        heading.setTextColor(Color.BLACK);
        content.addView(heading);

        TextView explainer = new TextView(this);
        explainer.setText("Share a page here, then open LinkBridge in the destination note.");
        explainer.setTextSize(17);
        explainer.setPadding(0, dp(10), 0, dp(16));
        content.addView(explainer);

        titleInput = new EditText(this);
        titleInput.setHint("Article title");
        titleInput.setSingleLine(true);
        content.addView(titleInput, new LinearLayout.LayoutParams(-1, -2));

        urlInput = new EditText(this);
        urlInput.setHint("https://example.com/article");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(false);
        urlInput.setMinLines(2);
        content.addView(urlInput, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this);
        start.setText("Send to Supernote");
        start.setOnClickListener(v -> sendFromFields());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(14);
        content.addView(start, buttonParams);

        Button stop = new Button(this);
        stop.setText("Stop for now");
        stop.setOnClickListener(v -> stopSending("Saved in the outbox for later."));
        content.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        Button discard = new Button(this);
        discard.setText("Discard this link");
        discard.setOnClickListener(v -> discardShown());
        content.addView(discard, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Ready.");
        status.setTextSize(17);
        status.setTextColor(Color.BLACK);
        status.setPadding(0, dp(18), 0, 0);
        content.addView(status);

        outboxStatus = new TextView(this);
        outboxStatus.setTextSize(15);
        outboxStatus.setTextColor(Color.DKGRAY);
        outboxStatus.setPadding(0, dp(8), 0, 0);
        content.addView(outboxStatus);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void handleIntent(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) {
            return;
        }
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.trim().isEmpty()) return;

        String url = extractUrl(shared);
        String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (!LinkItem.isHttpUrl(url)) {
            urlInput.setText(url);
            status.setText("The shared text did not contain a complete web address.");
            return;
        }
        if (url.getBytes(StandardCharsets.UTF_8).length > LinkItem.MAX_URL_BYTES) {
            urlInput.setText(url);
            status.setText("This URL is too long for the current Bluetooth format.");
            return;
        }

        LinkItem item = LinkItem.create(url, title);
        if (outbox.isFull()) {
            urlInput.setText(url);
            status.setText(outboxFullMessage());
            return;
        }
        if (!outbox.enqueue(item)) {
            status.setText("Could not save this link to the phone outbox.");
            return;
        }
        showItem(item);
        updateOutboxStatus();
        startSending(item);
    }

    static String extractUrl(String text) {
        Matcher matcher = HTTP_URL.matcher(text == null ? "" : text.trim());
        if (!matcher.find()) return text == null ? "" : text.trim();
        String candidate = matcher.group();
        while (!candidate.isEmpty() && ".,;!?".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    private void sendFromFields() {
        String url = urlInput.getText().toString().trim();
        if (!LinkItem.isHttpUrl(url)) {
            status.setText("Enter a complete http:// or https:// address.");
            return;
        }
        if (url.getBytes(StandardCharsets.UTF_8).length > LinkItem.MAX_URL_BYTES) {
            status.setText("This URL is too long for the current Bluetooth format.");
            return;
        }
        String cleanedTitle = LinkItem.cleanTitle(titleInput.getText().toString(), url);
        LinkItem latest = outbox.latest();
        LinkItem item = latest != null && latest.url.equals(url) && latest.title.equals(cleanedTitle)
            ? latest
            : LinkItem.create(url, cleanedTitle);
        if (latest == null || !latest.id.equals(item.id)) {
            if (outbox.isFull()) {
                status.setText(outboxFullMessage());
                return;
            }
            if (!outbox.enqueue(item)) {
                status.setText("Could not save this link to the phone outbox.");
                return;
            }
        }
        updateOutboxStatus();
        startSending(item);
    }

    private static String outboxFullMessage() {
        return "The outbox is full (" + LinkOutbox.MAX_ITEMS +
            " links). Send or discard waiting links first.";
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private void startSending(LinkItem item) {
        if (!hasBluetoothPermission()) {
            pendingPermissionItem = item;
            requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            }, PERMISSION_REQUEST);
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Turn on Bluetooth, then try again. The link remains in the outbox.");
            return;
        }
        if (!adapter.isMultipleAdvertisementSupported() || adapter.getBluetoothLeAdvertiser() == null) {
            status.setText("This phone cannot advertise the LinkBridge Bluetooth service.");
            return;
        }

        byte[] metadata;
        try {
            metadata = item.metadataJson(Math.max(0, outbox.count() - 1))
                .getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) {
            status.setText("Could not prepare this link for sending. It remains in the outbox.");
            return;
        }

        stopBluetoothTransport();
        activeItem = item;
        activeMetadata = metadata;
        servedDeviceAddress = null;
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        gattServer = manager.openGattServer(this, gattCallback);
        if (gattServer == null) {
            activeItem = null;
            status.setText("Could not start Bluetooth. The link remains in the outbox.");
            return;
        }

        BluetoothGattService service = new BluetoothGattService(
            SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(
            URL_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(
            METADATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ));
        service.addCharacteristic(new BluetoothGattCharacteristic(
            ACK_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE));
        if (!gattServer.addService(service)) {
            stopSending("Could not publish the link. It remains in the outbox.");
            return;
        }
        status.setText("Preparing Bluetooth…");
    }

    private void startAdvertising() {
        if (activeItem == null || gattServer == null) return;
        advertiser = adapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build();
        AdvertiseData data = new AdvertiseData.Builder()
            .addServiceUuid(new ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build();
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settings) {
            advertising = true;
            runOnUiThread(() -> status.setText("Ready. Open LinkBridge in the destination note."));
        }

        @Override
        public void onStartFailure(int errorCode) {
            runOnUiThread(() -> stopSending(
                "Bluetooth sender failed to start (code " + errorCode + "). The link remains queued."));
        }
    };

    private final BluetoothGattServerCallback gattCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int statusCode, BluetoothGattService service) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS && SERVICE_UUID.equals(service.getUuid())) {
                runOnUiThread(() -> startAdvertising());
            } else {
                runOnUiThread(() -> stopSending("Could not publish the Bluetooth service. The link remains queued."));
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> status.setText("Supernote connected…"));
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && activeItem != null) {
                runOnUiThread(() -> status.setText("Connection ended before save confirmation. Ready to retry."));
            }
        }

        @Override
        public void onCharacteristicReadRequest(
            BluetoothDevice device,
            int requestId,
            int offset,
            BluetoothGattCharacteristic characteristic) {
            BluetoothGattServer server = gattServer;
            if (server == null) return;
            LinkItem item = activeItem;
            if (item == null) {
                // Always answer, so the Supernote fails fast instead of waiting
                // for its Bluetooth timeout.
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                return;
            }
            byte[] value;
            boolean urlRead = false;
            try {
                if (URL_UUID.equals(characteristic.getUuid())) {
                    value = item.url.getBytes(StandardCharsets.UTF_8);
                    urlRead = true;
                } else if (METADATA_UUID.equals(characteristic.getUuid())) {
                    value = activeMetadata;
                    if (value == null) throw new IllegalStateException("No active metadata");
                } else {
                    server.sendResponse(
                        device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                    return;
                }
            } catch (Exception error) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                return;
            }
            if (offset < 0 || offset > value.length) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
                return;
            }
            if (urlRead) servedDeviceAddress = device.getAddress();
            server.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                Arrays.copyOfRange(value, offset, value.length));
            runOnUiThread(() -> status.setText("Link received. Waiting for the note to save…"));
        }

        @Override
        public void onCharacteristicWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {
            int result = BluetoothGatt.GATT_FAILURE;
            String acknowledgedId = null;
            LinkItem item = activeItem;
            boolean fromServedDevice = device.getAddress() != null &&
                device.getAddress().equals(servedDeviceAddress);
            if (item != null && ACK_UUID.equals(characteristic.getUuid()) && !preparedWrite && offset == 0 &&
                fromServedDevice) {
                try {
                    JSONObject ack = new JSONObject(new String(value, StandardCharsets.UTF_8));
                    boolean valid = ack.optInt("v", 0) == LinkItem.PROTOCOL_VERSION &&
                        "saved".equals(ack.optString("status")) &&
                        item.id.equals(ack.optString("id"));
                    if (valid && outbox.remove(item.id)) {
                        result = BluetoothGatt.GATT_SUCCESS;
                        acknowledgedId = item.id;
                    }
                } catch (Exception ignored) {
                }
            } else if (!ACK_UUID.equals(characteristic.getUuid())) {
                result = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            }

            if (responseNeeded && gattServer != null) {
                gattServer.sendResponse(device, requestId, result, offset, null);
            }
            if (result == BluetoothGatt.GATT_SUCCESS && acknowledgedId != null) {
                String finishedId = acknowledgedId;
                runOnUiThread(() -> {
                    // Stop advertising at once so the Supernote cannot reconnect
                    // to this finished item while it looks for the next one.
                    stopAdvertising();
                    status.postDelayed(() -> finishAcknowledgedItem(finishedId), 250);
                });
            }
        }
    };

    private void finishAcknowledgedItem(String finishedId) {
        LinkItem active = activeItem;
        // A newly shared link may already have replaced the finished one.
        if (active == null || !active.id.equals(finishedId)) return;
        stopBluetoothTransport();
        activeItem = null;
        LinkItem next = outbox.latest();
        updateOutboxStatus();
        if (next == null) {
            status.setText("Saved to the Supernote.");
            status.postDelayed(this::finishAndRemoveTask, 900);
        } else {
            // Keep going: the Supernote receives every waiting link while it is open.
            showItem(next);
            startSending(next);
        }
    }

    private void discardShown() {
        LinkItem active = activeItem;
        LinkItem shown = active != null ? active : outbox.latest();
        if (shown == null) {
            status.setText("The outbox is already empty.");
            return;
        }
        if (active != null) stopBluetoothTransport();
        if (!outbox.remove(shown.id)) {
            status.setText("Could not update the outbox.");
            return;
        }
        activeItem = null;
        showLatestItem();
        status.setText(outbox.count() == 0 ? "Outbox cleared." : "Link discarded.");
    }

    private void showLatestItem() {
        LinkItem latest = outbox.latest();
        if (latest != null) showItem(latest);
        else {
            titleInput.setText("");
            urlInput.setText("");
        }
        updateOutboxStatus();
    }

    private void showItem(LinkItem item) {
        titleInput.setText(item.title);
        urlInput.setText(item.url);
    }

    private void updateOutboxStatus() {
        int count = outbox.count();
        outboxStatus.setText(count == 0 ? "Outbox empty" : count + (count == 1 ? " link waiting" : " links waiting"));
    }

    private void stopSending(String message) {
        stopBluetoothTransport();
        activeItem = null;
        if (message != null && status != null) status.setText(message);
        updateOutboxStatus();
    }

    private void stopAdvertising() {
        if (advertiser != null && advertising) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception ignored) {
            }
        }
        advertising = false;
        advertiser = null;
    }

    private void stopBluetoothTransport() {
        stopAdvertising();
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception ignored) {
            }
            gattServer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != PERMISSION_REQUEST) return;
        LinkItem item = pendingPermissionItem;
        pendingPermissionItem = null;
        if (hasBluetoothPermission() && item != null) {
            startSending(item);
        } else {
            status.setText("Nearby devices permission is required. The link remains in the outbox.");
        }
    }

    @Override
    protected void onDestroy() {
        stopBluetoothTransport();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
