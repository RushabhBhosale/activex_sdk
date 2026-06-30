package com.iosx.activex;

public interface LefuEventListener {
  void onEvent(String eventName, ResultData data);

  void onDeviceInfo(ResultData data);
}
