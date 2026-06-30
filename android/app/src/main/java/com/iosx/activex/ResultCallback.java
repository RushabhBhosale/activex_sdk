package com.iosx.activex;

public interface ResultCallback {
  void onSuccess(ResultData data);

  void onError(String message, Throwable error);
}
