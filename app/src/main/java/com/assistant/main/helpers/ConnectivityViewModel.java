package com.assistant.main.helpers;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

//@Override
//protected void onCreate(Bundle savedInstanceState) {
//        ConnectivityViewModel vm = new ViewModelProvider(this).get(ConnectivityViewModel.class);
//
//        vm.getConnected().observe(this, connected -> {
//        // TODO change GUI depending on the connected value
//        });
//        }

public class ConnectivityViewModel extends AndroidViewModel {
    private final MutableLiveData<Boolean> mConnected = new MutableLiveData<>();

    public ConnectivityViewModel(Application app) {
        super(app);

        ConnectivityManager manager = (ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (manager != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH
                    )
                    .build();

            manager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
                public void onAvailable(@NonNull Network network) {
                    mConnected.postValue(true);
                }

                public void onLost(@NonNull Network network) {
                    mConnected.postValue(false);
                }

                public void onUnavailable() {
                    mConnected.postValue(false);
                }
            });
        } else {
            mConnected.setValue(true);
        }
    }

    @NonNull
    public MutableLiveData<Boolean> getConnected() {
        return mConnected;
    }
}
