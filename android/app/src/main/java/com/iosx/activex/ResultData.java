package com.iosx.activex;

import java.util.HashMap;

public class ResultData extends HashMap<String, Object> {
  public ResultData putValue(String key, Object value) {
    put(key, value);
    return this;
  }
}
