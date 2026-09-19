package com.amhdesign.pointercustomizer;

import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.system.Os;
import android.view.PointerIcon;
import java.lang.reflect.Method;

public class PointerUserService extends IPointerUserService.Stub {
    public PointerUserService() {}

    private Object getInputManager() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method getService = sm.getDeclaredMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "input");
        Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        return asInterface.invoke(null, binder);
    }

    @Override
    public boolean setCustomPointer(Bitmap bitmap, float hotspotX, float hotspotY) {
        try {
            if (bitmap == null) return false;
            PointerIcon icon = PointerIcon.create(bitmap, hotspotX, hotspotY);
            Object inputManager = getInputManager();
            Method set = inputManager.getClass().getMethod("setCustomPointerIcon", PointerIcon.class);
            set.invoke(inputManager, icon);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean resetPointer() {
        try {
            Object inputManager = getInputManager();
            Method set = inputManager.getClass().getMethod("setPointerIconType", int.class);
            set.invoke(inputManager, PointerIcon.TYPE_ARROW);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int getUid() {
        try { return Os.getuid(); }
        catch (Throwable t) { return Binder.getCallingUid(); }
    }

    @Override
    public void destroy() { System.exit(0); }
}