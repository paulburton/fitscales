package eu.paulburton.fitscales;

import java.util.ArrayList;

import eu.paulburton.fitscales.sync.FitBitSyncService;
import eu.paulburton.fitscales.sync.RunKeeperSyncService;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class FitscalesApplication extends Application
{
    public static FitscalesApplication inst;

    public SharedPreferences prefs;
    ArrayList<SyncService> syncServices;

    @Override
    public void onCreate()
    {
        super.onCreate();
        inst = this;

        prefs = getSharedPreferences("main", Context.MODE_PRIVATE);

        syncServices = new ArrayList<SyncService>();
        syncServices.add(new FitBitSyncService());
        syncServices.add(new RunKeeperSyncService());

        for (SyncService s : syncServices)
            s.load();
    }
}
