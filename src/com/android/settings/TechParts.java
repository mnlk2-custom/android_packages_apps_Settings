/*
* Copyright (C) 2010 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import com.android.settings.ShellInterface;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.IWindowManager;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TechParts extends PreferenceActivity 
implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "TechParts";
    private static final String TRACKBALL_WAKE_PREF = "pref_trackball_wake";
    private CheckBoxPreference mTrackballWakePref;
    private static final String TRACKBALL_UNLOCK_PREF = "pref_trackball_unlock";
    private CheckBoxPreference mTrackballUnlockPref;
    private static final String ADB_NOTIFY = "adb_notify";
    private CheckBoxPreference mAdbNotify;
    private static final String ADB_WIFI_PREF = "adb_wifi";
    private CheckBoxPreference mAdbWifiPref;    
    private static final String ADB_PORT = "5555";
    private static final String ELECTRON_BEAM_ANIMATION_ON = "electron_beam_animation_on";
    private CheckBoxPreference mElectronBeamAnimationOn;    
    private static final String ELECTRON_BEAM_ANIMATION_OFF = "electron_beam_animation_off"; 
    private CheckBoxPreference mElectronBeamAnimationOff;
    private static final String LOCKSCREEN_MUSIC_CONTROLS = "lockscreen_music_controls";
    private CheckBoxPreference mMusicControlPref;
    private static final String LOCKSCREEN_ALWAYS_MUSIC_CONTROLS = "lockscreen_always_music_controls";
    private CheckBoxPreference mAlwaysMusicControlPref;
    
    public ProgressDialog patience = null;
    final Handler mHandler = new Handler();
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.tech_parts);
        PreferenceScreen prefSet = getPreferenceScreen();

        /*Modversion*/
        setValueSummary("mod_version", "ro.modversion");
        
        /*System Info setting*/
        setStringSummary("device_cpu", getCPUInfo());
        setStringSummary("device_memory", getMemAvail().toString()+" MB / "+getMemTotal().toString()+" MB");        
        
        /*Trackball Wake*/ 
        mTrackballWakePref = (CheckBoxPreference) prefSet.findPreference(TRACKBALL_WAKE_PREF);
        mTrackballWakePref.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.TRACKBALL_WAKE_SCREEN, 0) == 1);

        /*Trackball Unlock*/
        mTrackballUnlockPref = (CheckBoxPreference) prefSet.findPreference(TRACKBALL_UNLOCK_PREF);
        mTrackballUnlockPref.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.TRACKBALL_UNLOCK_SCREEN, 0) == 1);
        
        /*WIFI ADB*/
        mAdbWifiPref = (CheckBoxPreference) findPreference(ADB_WIFI_PREF);
        mAdbWifiPref.setOnPreferenceChangeListener(this);       
        
        /*ADB Notify*/
        mAdbNotify = (CheckBoxPreference) findPreference(ADB_NOTIFY);
        
        /* Electron Beam control */
        boolean animateScreenLights = getResources().getBoolean(
                com.android.internal.R.bool.config_animateScreenLights);
        mElectronBeamAnimationOn = (CheckBoxPreference)prefSet.findPreference(ELECTRON_BEAM_ANIMATION_ON);
        mElectronBeamAnimationOn.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.ELECTRON_BEAM_ANIMATION_ON, 0) == 1);
        mElectronBeamAnimationOff = (CheckBoxPreference)prefSet.findPreference(ELECTRON_BEAM_ANIMATION_OFF);
        mElectronBeamAnimationOff.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.ELECTRON_BEAM_ANIMATION_OFF, 1) == 1);

        /* Music Controls */
        mMusicControlPref = (CheckBoxPreference) prefSet.findPreference(LOCKSCREEN_MUSIC_CONTROLS);
        mMusicControlPref.setChecked(Settings.System.getInt(getContentResolver(), 
                Settings.System.LOCKSCREEN_MUSIC_CONTROLS, 0) == 1);

        /* Always Display Music Controls */
        mAlwaysMusicControlPref = (CheckBoxPreference) prefSet.findPreference(LOCKSCREEN_ALWAYS_MUSIC_CONTROLS);
        mAlwaysMusicControlPref.setChecked(Settings.System.getInt(getContentResolver(), 
                Settings.System.LOCKSCREEN_ALWAYS_MUSIC_CONTROLS, 0) == 1);
        
        /* Hide Electron Beam controls if electron beam is disabled */
        if (animateScreenLights) {
            prefSet.removePreference(mElectronBeamAnimationOn);
            prefSet.removePreference(mElectronBeamAnimationOff);
        }        
    }
    
    protected void onResume() {
        super.onResume();
        mAdbNotify.setChecked(Settings.Secure.getInt(getContentResolver(),
        		Settings.Secure.ADB_NOTIFY, 1) != 0);
    }
    
    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary(
                getResources().getString(R.string.device_info_default));
        }
    }   
    
    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property,
                            getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {

        }
    }    
    
    private Long getMemTotal() {
    	Long total = null;
      BufferedReader reader = null;

      try {
         // Grab a reader to /proc/meminfo
         reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/meminfo")), 1000);

         // Grab the first line which contains mem total
         String line = reader.readLine();

         // Split line on the colon, we need info to the right of the colon
         String[] info = line.split(":");

         // We have to remove the kb on the end
         String[] memTotal = info[1].trim().split(" ");

    	// Convert kb into mb
         total = Long.parseLong(memTotal[0]);
         total = total / 1024;
      }
      catch(Exception e) {
         e.printStackTrace();
         // We don't want to return null so default to 0
         total = Long.parseLong("0");
      }
      finally {
         // Make sure the reader is closed no matter what
         try { reader.close(); }
         catch(Exception e) {}
         reader = null;
      }

      return total;
    }

    private Long getMemAvail() {
      Long avail = null;
      BufferedReader reader = null;

      try {
         // Grab a reader to /proc/meminfo
         reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/meminfo")), 1000);

    	// This is memTotal which we don't need
         String line = reader.readLine();

         // This is memFree which we need
         line = reader.readLine();
         String[] free = line.split(":");
         // Have to remove the kb on the end
         String [] memFree = free[1].trim().split(" ");

         // This is Buffers which we don't need
         line = reader.readLine();

         // This is Cached which we need
         line = reader.readLine();
         String[] cached = line.split(":");
         // Have to remove the kb on the end
         String[] memCached = cached[1].trim().split(" ");

         avail = Long.parseLong(memFree[0]) + Long.parseLong(memCached[0]);
         avail = avail / 1024;
      }
      catch(Exception e) {
         e.printStackTrace();
         // We don't want to return null so default to 0
         avail = Long.parseLong("0");
      }
      finally {
         // Make sure the reader is closed no matter what
         try { reader.close(); }
         catch(Exception e) {}
         reader = null;
      }

      return avail;
    }

   private String getCPUInfo() {
      String[] info = null;
      BufferedReader reader = null;

      try {
         // Grab a reader to /proc/cpuinfo
        reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/cpuinfo")), 1000);

         // Grab a single line from cpuinfo
         String line = reader.readLine();

         // Split on the colon, we need info to the right of colon
         info = line.split(":");
      }
      catch(IOException io) {
         io.printStackTrace();
         info = new String[1];
         info[1] = "error";
      }
      finally {
         // Make sure the reader is closed no matter what
         try { reader.close(); }
         catch(Exception e) {}
         reader = null;
      }

      return info[1];
    }    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;        
    	if (preference == mAdbNotify) {
    		Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADB_NOTIFY,
            		mAdbNotify.isChecked() ? 1 : 0);   
        } else if (preference == mMusicControlPref) {
            value = mMusicControlPref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_MUSIC_CONTROLS, value ? 1 : 0);
            return true;
        } else if (preference == mAlwaysMusicControlPref) {
            value = mAlwaysMusicControlPref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_ALWAYS_MUSIC_CONTROLS, value ? 1 : 0);
            return true;            
        } else if (preference == mTrackballWakePref) {
            value = mTrackballWakePref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.TRACKBALL_WAKE_SCREEN, value ? 1 : 0);
            return true;            
        } else if (preference == mTrackballUnlockPref) {
            value = mTrackballUnlockPref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.TRACKBALL_UNLOCK_SCREEN, value ? 1 : 0);
            return true;            
        } else if (preference == mAdbNotify) {
        	Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADB_NOTIFY,
        			mAdbNotify.isChecked() ? 1 : 0);
            return true;        	
        } else if (preference == mElectronBeamAnimationOn) {
            value = mElectronBeamAnimationOn.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.ELECTRON_BEAM_ANIMATION_ON, value ? 1 : 0);
            return true;            
        } else if (preference == mElectronBeamAnimationOff) {
            value = mElectronBeamAnimationOff.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.ELECTRON_BEAM_ANIMATION_OFF, value ? 1 : 0);
            return true;            
        }

        return false;
    }
    
     public boolean onPreferenceChange(Preference preference, Object objValue) {
         final String key = preference.getKey();    	 
         if (preference == mAdbWifiPref) {
     	    boolean have = mAdbWifiPref.isChecked();
     	    if (!have) {
     		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
     		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
     		String ipAddress = null;
     		if (wifiInfo != null) {
     		    long addr = wifiInfo.getIpAddress();
     		    if (addr != 0) {
     			// handle negative values whe first octet > 127
     			if (addr < 0) addr += 0x100000000L;
     			ipAddress = String.format("%d.%d.%d.%d", addr & 0xFF, (addr >> 8) & 0xFF, (addr >> 16) & 0xFF, (addr >> 24) & 0xFF);
     		    }
     		}
     		String[] commands = {
     		    "setprop service.adb.tcp.port " + ADB_PORT,
     		    "stop adbd",
     		    "start adbd"
     		};
     		sendshell(commands, false,
     			  getResources().getString(R.string.adb_instructions_on)
     			  .replaceFirst("%ip%", ipAddress)
     			  .replaceFirst("%P%", ADB_PORT));
     	    } else {
     		String[] commands = {
     		    "setprop service.adb.tcp.port -1",
     		    "stop adbd",
     		    "start adbd"
     		};
     		sendshell(commands, false, getResources().getString(R.string.adb_instructions_off));
     	    }
         }
            return true;
    }



           /**
        	* Methods for popups
        	*/

        	    public void toast(final CharSequence message) {
        	Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        	toast.show();
        	    }

        	    public void toastLong(final CharSequence message) {
        	Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
        	toast.show();
        	    }

        	    public void popup(final String title, final String message) {
        	Log.i(TAG, "popup");
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setTitle(title)
        	.setMessage(message)
        	.setCancelable(false)
        	.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int id) {
        	}
        	});
        	AlertDialog alert = builder.create();
        	alert.show();
        	    }

        	    final Runnable mNeedReboot = new Runnable() {
        	public void run() { needreboot(); }
        	};

        	    public void needreboot() {
        	Log.i(TAG, "needreboot");
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setMessage("Reboot is requiered to apply. Would you like to reboot now?")
        	.setCancelable(false)
        	.setPositiveButton(getResources().getString(R.string.yes),
        	new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int id) {
        	String[] commands = { "reboot" };
        	sendshell(commands, false, getResources().getString(R.string.rebooting));
        	}
        	})
        	.setNegativeButton(getResources().getString(R.string.no),
        	new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int id) {
        	}
        	});
        	AlertDialog alert = builder.create();
        	alert.show();
        	    }

        	    final Runnable mCommandFinished = new Runnable() {
        	public void run() { patience.cancel(); }
        	};

        	    public boolean sendshell(final String[] commands, final boolean reboot, final String message) {
        	if (message != null)
        	patience = ProgressDialog.show(this, "", message, true);
        	Thread t = new Thread() {
        	public void run() {
        	ShellInterface shell = new ShellInterface(commands);
        	shell.start();
        	while (shell.isAlive())
        	{
        	if (message != null)
        	patience.setProgress(shell.getStatus());
        	try {
        	Thread.sleep(500);
        	}
        	catch (InterruptedException e) {
        	e.printStackTrace();
        	}
        	}
        	if (message != null)
        	mHandler.post(mCommandFinished);
        	if (shell.interrupted())
        	popup(getResources().getString(R.string.error), getResources().getString(R.string.download_install_error));
        	if (reboot == true)
        	mHandler.post(mNeedReboot);
        	}
        	};
        	t.start();
        	return true;
        	    }
        	}

