package com.iosx.activex;

import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import android.content.Intent;

import androidx.core.view.WindowCompat;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(JambulLefuPlugin.class);
    registerPlugin(LefuPlugin.class);
    super.onCreate(savedInstanceState);

//    WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//      getWindow().setDecorFitsSystemWindows(false);
//    }
  }
}
