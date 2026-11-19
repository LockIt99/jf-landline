package com.jio.calling.gateway;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.jio.calling.sip.JioSipManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * Dual-protocol discovery broadcaster for the Jio Landline Gateway.
 * 1. mDNS advertisement via Android NsdManager (_jiolandline._tcp.)
 * 2. UDP broadcast listener on port 8089 (fallback for routers that isolate Wi-Fi multicast/mDNS across 2.4/5GHz bands)
 */
public class GatewayDiscoveryBroadcaster {
    private static final String TAG = "GatewayDiscoveryBroadcaster";
    public static final String SERVICE_TYPE = "_jiolandline._tcp.";
    public static final String SERVICE_NAME = "JioLandline-Gateway";
    public static final int UDP_DISCOVERY_PORT = 8089;
    public static final String DISCOVER_KEYWORD = "JIO_GATEWAY_DISCOVER";
    public static final String OFFER_PREFIX = "JIO_GATEWAY_OFFER";

    private final Context context;
    private final JioSipManager sipManager;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private WifiManager.MulticastLock multicastLock;
    private DatagramSocket udpSocket;
    private Thread udpThread;
    private volatile boolean isRunning = false;

    public GatewayDiscoveryBroadcaster(Context context) {
        this.context = context.getApplicationContext();
        this.sipManager = JioSipManager.getInstance(this.context);
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        acquireMulticastLock();
        startNsdAdvertisement();
        startUdpListener();
        Log.i(TAG, "GatewayDiscoveryBroadcaster started (mDNS + UDP fallback :8089)");
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;

        stopNsdAdvertisement();
        stopUdpListener();
        releaseMulticastLock();
        Log.i(TAG, "GatewayDiscoveryBroadcaster stopped");
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("jio:gateway_multicast_lock");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
                Log.d(TAG, "MulticastLock acquired");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire MulticastLock: " + e.getMessage());
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
                Log.d(TAG, "MulticastLock released");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing MulticastLock: " + e.getMessage());
        }
    }

    private void startNsdAdvertisement() {
        try {
            nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) {
                Log.w(TAG, "NsdManager not available on this device");
                return;
            }

            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(SERVICE_NAME);
            serviceInfo.setServiceType(SERVICE_TYPE);
            serviceInfo.setPort(GatewayServer.GATEWAY_PORT);

            if (Build.VERSION.SDK_INT >= 21) {
                String msisdn = (sipManager.getCurrentAccount() != null) ? sipManager.getCurrentAccount().getMsisdn() : "";
                if (msisdn != null && !msisdn.isEmpty()) {
                    serviceInfo.setAttribute("msisdn", msisdn);
                }
                serviceInfo.setAttribute("model", Build.MODEL);
            }

            registrationListener = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                    Log.i(TAG, "mDNS Service successfully registered: " + nsdServiceInfo.getServiceName());
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "mDNS Service registration failed: error code " + errorCode);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    Log.i(TAG, "mDNS Service unregistered: " + serviceInfo.getServiceName());
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "mDNS Service unregistration failed: error code " + errorCode);
                }
            };

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Error starting NsdManager advertisement: " + e.getMessage(), e);
        }
    }

    private void stopNsdAdvertisement() {
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering Nsd service: " + e.getMessage());
            }
            registrationListener = null;
        }
    }

    private void startUdpListener() {
        udpThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                udpSocket = new DatagramSocket(UDP_DISCOVERY_PORT);
                udpSocket.setBroadcast(true);
                Log.i(TAG, "UDP fallback discovery listening on port " + UDP_DISCOVERY_PORT);

                while (isRunning && udpSocket != null && !udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);

                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (msg.contains(DISCOVER_KEYWORD)) {
                        Log.i(TAG, "Received UDP discovery probe from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());

                        String msisdn = (sipManager.getCurrentAccount() != null) ? sipManager.getCurrentAccount().getMsisdn() : "";
                        String offerPayload = OFFER_PREFIX + ":" + GatewayServer.GATEWAY_PORT + ":" + (msisdn != null ? msisdn : "") + ":" + Build.MODEL;
                        byte[] offerBytes = offerPayload.getBytes(StandardCharsets.UTF_8);

                        DatagramPacket reply = new DatagramPacket(
                                offerBytes,
                                offerBytes.length,
                                packet.getAddress(),
                                packet.getPort()
                        );
                        udpSocket.send(reply);
                        Log.i(TAG, "Sent UDP offer to " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.w(TAG, "UDP discovery listener error: " + e.getMessage());
                }
            }
        }, "Gateway-UdpDiscovery");
        udpThread.start();
    }

    private void stopUdpListener() {
        try {
            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (Exception ignored) {}

        if (udpThread != null) {
            udpThread.interrupt();
            udpThread = null;
        }
    }
}
