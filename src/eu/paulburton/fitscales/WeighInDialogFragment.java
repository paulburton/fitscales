package eu.paulburton.fitscales;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableRow;
import android.widget.TextView;

public class WeighInDialogFragment extends DialogFragment
{
    private static final String TAG = "WeighInDialogFragment";
    private static final boolean DEBUG = false;

    private float weight, prevWeight;
    
    private TextView tvBmi, tvDelta, tvStatus;
    private Button btnSync, btnOk;
    private TableRow rowDelta;
    private LinearLayout llStatus;
    private ProgressBar pbStatus;
    
    private static SyncThread syncThread;

    static WeighInDialogFragment newInstance(float weight, float prevWeight)
    {
        WeighInDialogFragment f = new WeighInDialogFragment();
        Bundle args = new Bundle();
        args.putFloat("weight", weight);
        args.putFloat("prevWeight", prevWeight);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        weight = getArguments().getFloat("weight");
        prevWeight = getArguments().getFloat("prevWeight");

        Activity a = getActivity();
        if (a instanceof Listener)
            ((Listener)a).onWeighInDialogCreated();
    }

    @Override
    public void onDestroy()
    {
        Activity a = getActivity();
        if (a instanceof Listener)
            ((Listener)a).onWeighInDialogDestroyed();

        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Resources res = getResources();
        View v = inflater.inflate(R.layout.weighin_dialog, container, false);

        tvBmi = (TextView)v.findViewById(R.id.tvBmi);
        tvDelta = (TextView)v.findViewById(R.id.tvDelta);
        tvStatus = (TextView)v.findViewById(R.id.tvStatus);
        btnSync = (Button)v.findViewById(R.id.btnSync);
        btnOk = (Button)v.findViewById(R.id.btnOk);
        rowDelta = (TableRow)v.findViewById(R.id.rowDelta);
        llStatus = (LinearLayout)v.findViewById(R.id.llStatus);
        pbStatus = (ProgressBar)v.findViewById(R.id.pbStatus);

        double height = Prefs.getHeight().doubleValue();
        double bmi = weight / (height * height);
        tvBmi.setText(String.format(res.getString(R.string.dialog_weighin_bmival), bmi));
        
        btnSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                beginSync();
            }
        });
        
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                WeighInDialogFragment.this.dismiss();
            }
        });
        
        if (prevWeight < 0.0f)
            rowDelta.setVisibility(View.GONE);
        else {
            float delta = weight - prevWeight;
            tvDelta.setText(weightString(delta, Prefs.getWeightUnit()));
        }
        
        setTitle();
        updateSyncViews();

        return v;
    }
    
    @Override
    public void onStart()
    {
        super.onStart();
        SyncThread st = syncThread;
        if (st != null)
            st.dialog = this;
        
        if (Prefs.getSyncAuto() && !getArguments().getBoolean("synced", false))
            beginSync();
        updateSyncViews();
    }
    
    @Override
    public void onStop()
    {
        SyncThread st = syncThread;
        if (st != null)
            st.dialog = null;
        super.onStop();
    }
    
    private void setTitle()
    {
        Resources res = getResources();
        int unit = Prefs.getWeightUnit();
        getDialog().setTitle(String.format(res.getString(R.string.dialog_weighin_title), weightString(weight, unit)));
    }
    
    private String weightString(float weight, int unit)
    {
        Resources res = getResources();
        String[] fmts = res.getStringArray(R.array.weight_units_formatter_array);
        float main, sub = 0.0f;
        boolean neg = false;
        String wstr;
        
        if (weight < 0.0f) {
            neg = true;
            weight = -weight;
        }
        
        if (unit == Prefs.UNIT_KG)
            main = weight;
        else {
            float lbs = weight * 2.20462262f;
            
            if (lbs < 14.0f)
                unit = Prefs.UNIT_LB;
            
            if (unit == Prefs.UNIT_LB)
                main = lbs;
            else {
                /* UNIT_STONE */
                int stone = (int)(lbs / 14);
                main = stone;
                sub = lbs - (stone * 14);
            }
        }
        
        wstr = String.format(fmts[unit], main, sub);
        if (neg)
            wstr = "-" + wstr;
        return wstr;
    }
    
    private void updateSyncViews()
    {
        SyncThread st = syncThread;
        Resources res = getResources();

        if (st != null) {
            /* currently syncing */
            if (st.svc != null) {
                String fmt;
                if (st.failed)
                    fmt = res.getString(R.string.dialog_weighin_status_syncfail);
                else
                    fmt = res.getString(R.string.dialog_weighin_status_syncing);
                tvStatus.setText(String.format(fmt, st.svc.name));
            }
            btnSync.setEnabled(false);
            pbStatus.setVisibility(View.VISIBLE);
            llStatus.setVisibility(View.VISIBLE);
            return;
        }
        
        if (getArguments().getBoolean("synced", false)) {
            /* already synced */
            tvStatus.setText(res.getString(R.string.dialog_weighin_status_synced));
            btnSync.setEnabled(false);
            pbStatus.setVisibility(View.GONE);
            llStatus.setVisibility(View.VISIBLE);
            return;
        }
        
        int enabledCount = 0;
        for (SyncService s : FitscalesApplication.inst.syncServices) {
            if (s.enabled)
                enabledCount++;
        }
        
        if (enabledCount == 0) {
            /* no enabled sync services */
            btnSync.setEnabled(false);
            llStatus.setVisibility(View.INVISIBLE);
            return;
        }
        
        /* not syncing, but have enabled services */
        llStatus.setVisibility(View.INVISIBLE);
        btnSync.setEnabled(true);
    }
    
    private void beginSync()
    {
        if (syncThread != null)
            return;
        
        syncThread = new SyncThread(weight);
        syncThread.dialog = this;
        syncThread.start();

        getArguments().putBoolean("synced", true);
    }
    
    private class SyncThread extends Thread
    {
        volatile SyncService svc;
        volatile WeighInDialogFragment dialog;
        volatile boolean failed;
        float weight;
        
        public SyncThread(float weight)
        {
            super();
            this.weight = weight;

            // Bodge to begin with svc set to the first enabled SyncService
            for (SyncService svc : FitscalesApplication.inst.syncServices) {
                if (!svc.enabled)
                    continue;
                this.svc = svc;
                break;
            }
        }

        @Override
        public void run()
        {
            try {
                doSync();
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Sync failure", ex);
            }
            
            WeighInDialogFragment.syncThread = null;
            updateDialog();
            svc = null;
            dialog = null;
        }
        
        private void doSync()
        {
            for (SyncService svc : FitscalesApplication.inst.syncServices) {
                if (!svc.enabled)
                    continue;
                
                this.svc = svc;
                this.failed = false;
                updateDialog();
                
                if (!svc.syncWeight(weight)) {
                    this.failed = true;
                    updateDialog();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
        private void updateDialog()
        {
            final WeighInDialogFragment dlg = this.dialog;
            if (dlg != null) {
                dlg.tvStatus.post(new Runnable() {
                    @Override
                    public void run()
                    {
                        dlg.updateSyncViews();
                    }
                });
            }
        }
    }
    
    public interface Listener
    {
        public void onWeighInDialogCreated();
        public void onWeighInDialogDestroyed();
    }
}
