package com.amhdesign.pointercustomizer;

import android.graphics.Bitmap;

interface IPointerUserService {
    boolean setCustomPointer(Bitmap bitmap, float hotspotX, float hotspotY);
    boolean resetPointer();
    int getUid();
    void destroy();
}