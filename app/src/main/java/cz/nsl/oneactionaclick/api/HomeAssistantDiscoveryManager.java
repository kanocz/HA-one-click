package cz.nsl.oneactionaclick.api;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager class to handle discovery of Home Assistant instances on the local network
 * using Android's Network Service Discovery (NSD) API.
 */
public class HomeAssistantDiscoveryManager {
    private static final String TAG = "HADiscoveryManager";
    private static final String SERVICE_TYPE = "_home-assistant._tcp.";
    private static final int DISCOVERY_TIMEOUT_MS = 10000; // 10 seconds

    private final Context mContext;
    private final NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private final List<HomeAssistantInstance> mDiscoveredInstances;
    private HomeAssistantDiscoveryListener mListener;
    private final Handler mHandler;
    private boolean mIsDiscovering = false;

    public HomeAssistantDiscoveryManager(Context context) {
        mContext = context.getApplicationContext();
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        mDiscoveredInstances = new ArrayList<>();
        mHandler = new Handler(Looper.getMainLooper());
    }

    public interface HomeAssistantDiscoveryListener {
        void onDiscoveryStarted();
        void onInstanceFound(HomeAssistantInstance instance);
        void onDiscoveryFinished(List<HomeAssistantInstance> instances);
        void onDiscoveryFailed(String errorMessage);
    }

    public static class HomeAssistantInstance {
        private final String serviceName;
        private final String hostAddress;
        private final int port;
        private final String url;

        public HomeAssistantInstance(String serviceName, String hostAddress, int port) {
            this.serviceName = serviceName;
            this.hostAddress = hostAddress;
            this.port = port;
            this.url = "http://" + hostAddress + ":" + port;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getHostAddress() {
            return hostAddress;
        }

        public int getPort() {
            return port;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public String toString() {
            return serviceName + " (" + url + ")";
        }
    }

    public void startDiscovery(HomeAssistantDiscoveryListener listener) {
        if (mIsDiscovering) {
            stopDiscovery();
        }

        mListener = listener;
        mDiscoveredInstances.clear();

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start discovery failed with error code: " + errorCode);
                mIsDiscovering = false;
                if (mListener != null) {
                    mListener.onDiscoveryFailed("Failed to start discovery: error " + errorCode);
                }
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed with error code: " + errorCode);
                mIsDiscovering = false;
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started for: " + serviceType);
                mIsDiscovering = true;
                if (mListener != null) {
                    mListener.onDiscoveryStarted();
                }

                // Set a timeout for discovery
                mHandler.postDelayed(() -> {
                    if (mIsDiscovering) {
                        stopDiscovery();
                        if (mListener != null) {
                            mListener.onDiscoveryFinished(mDiscoveredInstances);
                        }
                    }
                }, DISCOVERY_TIMEOUT_MS);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped for: " + serviceType);
                mIsDiscovering = false;
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                
                // Only resolve if it looks like a Home Assistant service
                if (serviceInfo.getServiceType().equals(SERVICE_TYPE)) {
                    mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed for " + serviceInfo.getServiceName() + ": " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            Log.d(TAG, "Resolved service: " + serviceInfo.getServiceName());
                            
                            InetAddress host = serviceInfo.getHost();
                            int port = serviceInfo.getPort();
                            
                            HomeAssistantInstance instance = new HomeAssistantInstance(
                                    serviceInfo.getServiceName(),
                                    host.getHostAddress(),
                                    port
                            );
                            
                            // Add to our list if not already there
                            boolean alreadyDiscovered = false;
                            for (HomeAssistantInstance discovered : mDiscoveredInstances) {
                                if (discovered.getUrl().equals(instance.getUrl())) {
                                    alreadyDiscovered = true;
                                    break;
                                }
                            }
                            
                            if (!alreadyDiscovered) {
                                mDiscoveredInstances.add(instance);
                                if (mListener != null) {
                                    mListener.onInstanceFound(instance);
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
            }
        };

        try {
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        } catch (Exception e) {
            Log.e(TAG, "Error starting discovery", e);
            if (mListener != null) {
                mListener.onDiscoveryFailed("Error starting discovery: " + e.getMessage());
            }
        }
    }

    public void stopDiscovery() {
        if (mDiscoveryListener != null && mIsDiscovering) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
            mIsDiscovering = false;
        }
    }

    public boolean isDiscovering() {
        return mIsDiscovering;
    }

    public List<HomeAssistantInstance> getDiscoveredInstances() {
        return new ArrayList<>(mDiscoveredInstances);
    }

    /**
     * Manually add a Home Assistant instance by URL.
     * This is useful for cases where automatic discovery doesn't work.
     * 
     * @param url The URL in format "http://hostname:port"
     * @return The created HomeAssistantInstance if valid, null otherwise
     */
    public HomeAssistantInstance addManualInstance(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            url = url.trim();
            
            // Add http:// prefix if missing
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            
            // Parse the URL
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            int port = parsedUrl.getPort();
            if (port == -1) {
                port = parsedUrl.getDefaultPort();
                if (port == -1) {
                    // Use default Home Assistant port if not specified
                    port = 8123;
                }
            }
            
            HomeAssistantInstance instance = new HomeAssistantInstance(
                    host,
                    host,
                    port
            );
            
            return instance;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing manual instance URL", e);
            return null;
        }
    }

    /**
     * Check if a manually entered URL is valid for a Home Assistant instance.
     * 
     * @param url The URL to validate
     * @return true if the URL appears valid
     */
    public boolean isValidHomeAssistantUrl(String url) {
        return addManualInstance(url) != null;
    }
}