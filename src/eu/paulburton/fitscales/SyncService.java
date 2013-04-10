package eu.paulburton.fitscales;

import android.content.SharedPreferences;
import android.os.Build;

public class SyncService
{
    private static final String KEY_USER = "_user";

    public String name, prefName;
    public boolean enabled;
    public String user;

    protected SyncService(String name, String prefName)
    {
        this.name = name;
        this.prefName = prefName;
        this.enabled = false;
    }
    
    public void load()
    {
        user = FitscalesApplication.inst.prefs.getString(prefName + KEY_USER, null);
    }
    
    public void save()
    {
        SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
        edit.putString(prefName + KEY_USER, user);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
            edit.apply();
        else
            edit.commit();
    }
    
    public void connect()
    {
    }
    
    public void disconnect()
    {
    }
    
    public boolean isConnecting()
    {
        return false;
    }
    
    public boolean syncWeight(float weight)
    {
        return false;
    }
}
