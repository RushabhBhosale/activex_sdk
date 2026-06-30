package com.iosx.activex;

import com.lefu.ppbase.PPScaleHelper;

/**
 * The Secret is tied to the application package.
 * Make sure to replace these secrets with your own.
 */
public class SecretManager {

  private static final String SECRET_TYPE_4 = "nvGK80/6QNYL3B+CLEtvHXkRkBSBXmGuEL3Pl2fhPWakk5x4U2OL6tGHkxbPpKLG";
  private static final String SECRET_TYPE_8 = "YIhowKVo3qjhQRke0MKUgDiYdNBCKtzS8KRtqeDz12OjgZjAfQLBSH/Sm9ESJBau";

  public static String getSecret(int calculateType) {
    if (PPScaleHelper.INSTANCE.isCalcute8(calculateType)) {
      return SECRET_TYPE_8;
    } else {
      return SECRET_TYPE_4;
    }
  }
}

