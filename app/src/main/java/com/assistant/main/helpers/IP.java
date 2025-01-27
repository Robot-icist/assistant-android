package com.assistant.main.helpers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class IP {

    public static String getCellularIPv4Address(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                // Get the active network
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    // Get the network capabilities for the active network
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        // It is a cellular network, now fetch the IPv4 address
                        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                        for (NetworkInterface networkInterface : interfaces) {
                            List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                            for (InetAddress address : addresses) {
                                if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                                    return address.getHostAddress();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if no cellular network or address is found
    }
}
