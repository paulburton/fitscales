package eu.paulburton.fitscales;

import java.math.BigDecimal;
import java.util.List;

import com.actionbarsherlock.app.SherlockFragment;
import eu.paulburton.fitscales.sync.OAuthSyncService;
import eu.paulburton.fitscales.widget.Switch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TableRow;
import android.widget.TextView;

public class SettingsFragment extends SherlockFragment
{
    private static final String TAG = "SettingsFragment";
    private static final boolean DEBUG = false;

    private EditText editHeightMain;
    private EditText editHeightSub;
    private TableRow rowHeightSub;
    private Spinner spinHeightUnit;
    private Spinner spinWeightUnit;
    private SeekBar seekStabilityPrecision;
    private TextView textHeightSubUnit;
    private ListView listSyncServices;
    private Switch swSyncAuto;
    private Button btnDonate;

    public SettingsFragment()
    {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.settings_fragment, container, false);

        editHeightMain = (EditText)v.findViewById(R.id.editHeightMain);
        editHeightSub = (EditText)v.findViewById(R.id.editHeightSub);
        rowHeightSub = (TableRow)v.findViewById(R.id.rowHeightSub);
        spinHeightUnit = (Spinner)v.findViewById(R.id.spinHeightUnit);
        spinWeightUnit = (Spinner)v.findViewById(R.id.spinWeightUnit);
        seekStabilityPrecision = (SeekBar)v.findViewById(R.id.seekStabilityPrecision);
        textHeightSubUnit = (TextView)v.findViewById(R.id.textHeightSubUnit);
        listSyncServices = (ListView)v.findViewById(R.id.listSyncServices);
        swSyncAuto = (Switch)v.findViewById(R.id.swSyncAuto);
        btnDonate = (Button)v.findViewById(R.id.btnDonate);

        TabHost tabHost = (TabHost)v.findViewById(android.R.id.tabhost);
        tabHost.setup();

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (DEBUG)
                Log.d(TAG, "Using horizontal layout");

            LinearLayout llMain = (LinearLayout)v.findViewById(R.id.llMain);
            llMain.setOrientation(LinearLayout.HORIZONTAL);

            TabWidget tw = tabHost.getTabWidget();
            tw.setOrientation(LinearLayout.VERTICAL);
            tw.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.0f));

            FrameLayout tc = tabHost.getTabContentView();
            tc.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        }

        tabHost.addTab(tabHost.newTabSpec("tab_body")
                .setIndicator(createIndicatorView(tabHost, "Body", null, inflater)).setContent(R.id.tabViewBody));
        tabHost.addTab(tabHost.newTabSpec("tab_sync")
                .setIndicator(createIndicatorView(tabHost, "Sync", null, inflater)).setContent(R.id.tabViewSync));
        tabHost.addTab(tabHost.newTabSpec("tab_about")
                .setIndicator(createIndicatorView(tabHost, "About", null, inflater)).setContent(R.id.tabViewAbout));
        tabHost.setCurrentTab(0);

        ArrayAdapter<CharSequence> heightUnitAdapter = ArrayAdapter.createFromResource(inflater.getContext(),
                R.array.height_units_main_array, android.R.layout.simple_spinner_item);
        heightUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinHeightUnit.setAdapter(heightUnitAdapter);
        spinHeightUnit.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
                edit.putInt(Prefs.KEY_HEIGHTUNIT, spinHeightUnit.getSelectedItemPosition());
                Prefs.save(edit);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        editHeightMain.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                saveHeight();
            }
        });

        editHeightSub.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                saveHeight();
            }
        });

        ArrayAdapter<CharSequence> weightUnitAdapter = ArrayAdapter.createFromResource(inflater.getContext(),
                R.array.weight_units_main_array, android.R.layout.simple_spinner_item);
        weightUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinWeightUnit.setAdapter(weightUnitAdapter);
        spinWeightUnit.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
                edit.putInt(Prefs.KEY_WEIGHTUNIT, spinWeightUnit.getSelectedItemPosition());
                Prefs.save(edit);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });
        
        final float stabilityPrecisionStep = 0.1f;

        seekStabilityPrecision.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if (!fromUser)
                    return;

                float precision = Prefs.STABILITY_PRECISION_MIN + (progress * stabilityPrecisionStep);
                if (precision == Prefs.getStabilityPrecision())
                    return;

                if (DEBUG)
                    Log.d(TAG, "Stability precision change to " + precision);

                SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
                edit.putFloat(Prefs.KEY_STABILITY_PRECISION, precision);
                Prefs.save(edit);
            }
        });
        seekStabilityPrecision.setMax((int)((Prefs.STABILITY_PRECISION_MAX - Prefs.STABILITY_PRECISION_MIN) / stabilityPrecisionStep) + 1);
        seekStabilityPrecision.setProgress((int)((Prefs.getStabilityPrecision() - Prefs.STABILITY_PRECISION_MIN) / stabilityPrecisionStep));

        swSyncAuto.setChecked(Prefs.getSyncAuto());
        swSyncAuto.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked == Prefs.getSyncAuto())
                    return;

                SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
                edit.putBoolean(Prefs.KEY_SYNCAUTO, isChecked);
                Prefs.save(edit);
            }
        });
        
        btnDonate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=396BVJNLQFD62&lc=GB&item_name=FitScales%20Android%20App&currency_code=GBP&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"));
                startActivity(intent);
            }
        });
        
        /* Prevent onClick being passed up to the settingsView */
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
            }
        });
        
        listSyncServices.setAdapter(new SyncServiceAdapter(this, inflater.getContext(), R.layout.settings_sync_item, FitscalesApplication.inst.syncServices));

        loadSettings();

        return v;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        loadSettings();
        FitscalesApplication.inst.prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override
    public void onStop()
    {
        FitscalesApplication.inst.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);

        super.onStop();
    }

    public void loadSettings()
    {
        spinHeightUnit.setSelection(Math.min(Prefs.getHeightUnit(), spinHeightUnit.getCount() - 1));
        spinWeightUnit.setSelection(Math.min(Prefs.getWeightUnit(), spinWeightUnit.getCount() - 1));
        setupHeightEntry();
    }

    private View createIndicatorView(TabHost tabHost, CharSequence label, Drawable icon, LayoutInflater inflater)
    {
        View tabIndicator = inflater.inflate(R.layout.tab_indicator, tabHost.getTabWidget(), false);

        final TextView tv = (TextView)tabIndicator.findViewById(R.id.title);
        tv.setText(label);

        final ImageView iconView = (ImageView)tabIndicator.findViewById(R.id.icon);
        iconView.setImageDrawable(icon);

        return tabIndicator;
    }

    private void setupHeightEntry()
    {
        int unit = spinHeightUnit.getSelectedItemPosition();
        Resources res = getResources();
        String[] sub_units = res.getStringArray(R.array.height_units_sub_array);

        if (DEBUG)
            Log.d(TAG, "setupHeightEntry to unit " + unit);

        BigDecimal heightM = Prefs.getHeight();

        if (unit == Prefs.UNIT_M) {
            editHeightMain.setText(heightM.stripTrailingZeros().toPlainString());
        } else {
            BigDecimal heightIn = heightM.multiply(new BigDecimal(39.3700787));

            if (unit == Prefs.UNIT_INCH) {
                editHeightMain.setText(heightIn.setScale(4, BigDecimal.ROUND_HALF_UP).stripTrailingZeros()
                        .toPlainString());
            } else {
                /* Prefs.UNIT_FEET */
                int heightFeet = heightIn.divide(new BigDecimal(12), BigDecimal.ROUND_DOWN).intValue();
                heightIn = heightIn.subtract(new BigDecimal(heightFeet * 12)).setScale(4, BigDecimal.ROUND_HALF_UP);
                editHeightMain.setText(Integer.toString(heightFeet));
                editHeightSub.setText(heightIn.stripTrailingZeros().toPlainString());
            }
        }

        if ("".equals(sub_units[unit]))
            rowHeightSub.setVisibility(View.GONE);
        else {
            textHeightSubUnit.setText(sub_units[unit]);
            rowHeightSub.setVisibility(View.VISIBLE);
        }
    }

    private void saveHeight()
    {
        int unit = spinHeightUnit.getSelectedItemPosition();
        BigDecimal heightM;

        try {
            if (unit == Prefs.UNIT_M)
                heightM = new BigDecimal(editHeightMain.getText().toString()).setScale(4, BigDecimal.ROUND_HALF_UP);
            else {
                BigDecimal heightIn = new BigDecimal(editHeightMain.getText().toString()).setScale(4,
                        BigDecimal.ROUND_HALF_UP);

                if (unit == Prefs.UNIT_FEET) {
                    if (rowHeightSub.getVisibility() != View.VISIBLE) {
                        if (DEBUG)
                            Log.d(TAG, "Skipping height save whilst sub row isn't visible");
                        return;
                    }

                    heightIn = heightIn.multiply(new BigDecimal(12));
                    heightIn = heightIn.add(new BigDecimal(editHeightSub.getText().toString()).setScale(4,
                            BigDecimal.ROUND_HALF_UP));
                }

                heightM = heightIn.divide(new BigDecimal(39.3700787), BigDecimal.ROUND_HALF_UP).setScale(4,
                        BigDecimal.ROUND_HALF_UP);
            }
        } catch (Exception ex) {
            if (DEBUG)
                Log.e(TAG, "Failed to parse height", ex);
            return;
        }

        if (heightM.equals(Prefs.getHeight()))
            return;

        if (DEBUG)
            Log.d(TAG, "Saving height " + heightM.toString());

        SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
        edit.putString(Prefs.KEY_HEIGHT, heightM.toString());
        Prefs.save(edit);
    }

    private final OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
        {
            if (Prefs.KEY_HEIGHTUNIT.equals(key)) {
                spinHeightUnit.post(new Runnable() {
                    @Override
                    public void run()
                    {
                        setupHeightEntry();
                    }
                });
            }
        }
    };
    
    private class SyncServiceAdapter extends ArrayAdapter<SyncService>
    {
        private SettingsFragment frag;
        private List<SyncService> list;
        private Resources res;
        private Handler handler;

        public SyncServiceAdapter(SettingsFragment frag, Context context, int textViewResourceId, List<SyncService> list)
        {
            super(context, textViewResourceId, list);
            
            this.frag = frag;
            this.list = list;
            this.res = context.getResources();
            this.handler = new Handler();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View v = convertView;
            ItemWrapper wrap = null;

            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.settings_sync_item, null);
            }
            
            Object tag = v.getTag();
            if (tag instanceof ItemWrapper)
                wrap = (ItemWrapper)tag;
            else {
                wrap = new ItemWrapper();
                wrap.swEnable = (Switch)v.findViewById(R.id.swEnable);
                wrap.tvStatus = (TextView)v.findViewById(R.id.tvStatus);
                v.setTag(wrap);
            }

            SyncService svc = list.get(position);
            if (svc != null) {
                boolean connecting = svc.isConnecting();

                // Ensure listeners don't do anything
                wrap.swEnable.setTag(null);
                
                wrap.swEnable.setText(svc.name);
                wrap.swEnable.setChecked(svc.enabled || connecting);
                wrap.swEnable.setEnabled(!connecting);
                wrap.swEnable.setOnCheckedChangeListener(checkListener);

                if (connecting)
                    wrap.tvStatus.setText(String.format(res.getString(R.string.settings_sync_connecting), svc.user));
                else if (svc.enabled)
                    wrap.tvStatus.setText(String.format(res.getString(R.string.settings_sync_enabled), svc.user));
                else
                    wrap.tvStatus.setText(res.getString(R.string.settings_sync_disabled));

                wrap.swEnable.setTag(svc);
            }

            return v;
        }
        
        private final OnCheckedChangeListener checkListener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                Object tag = buttonView.getTag();
                if (!(tag instanceof SyncService)) {
                    if (DEBUG)
                        Log.d(TAG, "Button tag isn't a SyncService");
                    return;
                }
                SyncService svc = (SyncService)tag;
                
                if (isChecked == svc.enabled)
                    return;
                
                if (DEBUG)
                    Log.d(TAG, "Set service " + svc.name + " " + (isChecked ? "enabled" : "disabled"));
                
                if (svc instanceof OAuthSyncService)
                    ((OAuthSyncService)svc).setListener(oaSvcListener);
                
                if (isChecked) {
                    svc.connect();
                } else {
                    svc.disconnect();
                }
                
                SyncServiceAdapter.this.notifyDataSetChanged();
            }
        };
        
        private final OAuthSyncService.Listener oaSvcListener = new OAuthSyncService.Listener() {
            @Override
            public void oauthShowPage(final OAuthSyncService svc, final String url)
            {
                SyncServiceAdapter.this.handler.post(new Runnable() {
                    public void run()
                    {
                        Activity a = SyncServiceAdapter.this.frag.getActivity();
                        if (a instanceof SettingsFragment.Listener)
                            ((SettingsFragment.Listener)a).oauthShowPage(svc, url);
                    }
                });
            }

            @Override
            public void oauthDone(OAuthSyncService svc)
            {
                SyncServiceAdapter.this.handler.post(new Runnable() {
                    public void run()
                    {
                        SyncServiceAdapter.this.notifyDataSetChanged();
                    }
                });
            }
        };
        
        private class ItemWrapper
        {
            Switch swEnable;
            TextView tvStatus;
        }
    }
    
    public interface Listener
    {
        public void oauthShowPage(OAuthSyncService svc, String url);
    }
}
