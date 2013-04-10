package eu.paulburton.fitscales;

import java.math.BigDecimal;
import java.util.Locale;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class Prefs
{
    private static final String TAG = "Prefs";
    private static final boolean DEBUG = false;

    public static final int UNIT_KG = 0;
    public static final int UNIT_LB = 1;
    public static final int UNIT_STONE = 2;

    public static final int UNIT_M = 0;
    public static final int UNIT_INCH = 1;
    public static final int UNIT_FEET = 2;

    public static final float STABILITY_PRECISION_MIN = 0.2f;
    public static final float STABILITY_PRECISION_DEF = 1.0f;
    public static final float STABILITY_PRECISION_MAX = 1.8f;

    static final String KEY_HEIGHTUNIT = "height_unit";
    static final String KEY_WEIGHTUNIT = "weight_unit";
    static final String KEY_HEIGHT = "height";
    static final String KEY_LASTWEIGHT = "weight_last";
    static final String KEY_SYNCAUTO = "sync_auto";
    static final String KEY_STABILITY_PRECISION = "stability_precision";

    public static int getHeightUnit()
    {
        int def = UNIT_M;
        if (Locale.getDefault().equals(Locale.UK))
            def = UNIT_FEET;
        return FitscalesApplication.inst.prefs.getInt(KEY_HEIGHTUNIT, def);
    }

    public static int getWeightUnit()
    {
        int def = UNIT_KG;
        if (Locale.getDefault().equals(Locale.UK))
            def = UNIT_STONE;
        return FitscalesApplication.inst.prefs.getInt(KEY_WEIGHTUNIT, def);
    }

    public static BigDecimal getHeight()
    {
        try {
            return new BigDecimal(FitscalesApplication.inst.prefs.getString(KEY_HEIGHT, "1.778")).setScale(4, BigDecimal.ROUND_HALF_UP);
        } catch (Exception ex) {
            if (DEBUG)
                Log.d(TAG, "Failed to parse height", ex);
            return new BigDecimal(1.778).setScale(4, BigDecimal.ROUND_HALF_UP);
        }
    }
    
    public static float getLastWeight()
    {
        try {
            return FitscalesApplication.inst.prefs.getFloat(KEY_LASTWEIGHT, -1.0f);
        } catch (Exception ex) {
            if (DEBUG)
                Log.d(TAG, "Failed to parse weight", ex);
            return -1.0f;
        }
    }
    
    public static boolean getSyncAuto()
    {
        return FitscalesApplication.inst.prefs.getBoolean(KEY_SYNCAUTO, false);
    }
    
    public static float getStabilityPrecision()
    {
        try {
            return FitscalesApplication.inst.prefs.getFloat(KEY_STABILITY_PRECISION, 1.0f);
        } catch (Exception ex) {
            if (DEBUG)
                Log.d(TAG, "Failed to parse stability precision", ex);
            return 1.0f;
        }
    }

    public static void save(SharedPreferences.Editor edit)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
            edit.apply();
        else
            edit.commit();
    }
}
