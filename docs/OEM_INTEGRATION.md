# OEM Integration Reference: OriginOS & iQOO Platform
## Architectural Blueprint for Native Camera & System Server Integration

> [!IMPORTANT]
> **Prototype vs Production Boundary:**  
> The hackathon prototype runs as a standard user-space Android APK using CameraX. It does not attempt to illegally bypass Android permissions, patch private camera libraries, or modify system partitions.  
> This document details the exact technical roadmap for how **vivo / iQOO engineers** can natively incorporate this engine into OriginOS.

---

### 1. Integration Topology

In an OriginOS production build, the Wellness AI Engine transitions from an app-bundled library to an Android OS System Service managed by `system_server`.

```
+--------------------------------------------------------------------------------+
| OriginOS System Image                                                          |
|                                                                                |
|  [ Native iQOO Camera ]  (com.android.camera / system app)                     |
|           │                                                                    |
|           ▼ (Camera HAL Frame Stream)                                          |
|  [ Camera Framework ]                                                          |
|           │                                                                    |
|           ▼ (android.wellness.WellnessManager)                                 |
|  [ AIDL Proxy (Binder) ]                                                       |
|           │                                                                    |
|═══════════╪════════════════════════════════════════════════════════════════════|
|           ▼ [Binder IPC Boundary]                                              |
|  [ system_server ]                                                             |
|       └── [ WellnessManagerService ] (implements IWellnessManager.Stub)        |
|                │                                                               |
|                ▼ (JNI / Native Bridge)                                         |
|       [ libwellness_engine.so ]                                                |
|                │                                                               |
|                ▼ (Hexagon DSP / Qualcomm NPU HAL)                              |
|       [ Qualcomm Neural Processing Engine (QNN) ]                              |
+--------------------------------------------------------------------------------+
```

---

### 2. Android Framework & System Server Components

#### A. Service Registration in `SystemServer.java`
During Android OS boot, `system_server` initializes core framework services:
```java
// frameworks/base/services/java/com/android/server/SystemServer.java
private void startOtherServices() {
    ...
    traceBeginAndSlog("StartWellnessManagerService");
    try {
        ServiceManager.addService(Context.WELLNESS_SERVICE, 
            new WellnessManagerService(context));
    } catch (Throwable e) {
        reportWtf("starting WellnessManagerService", e);
    }
    Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    ...
}
```

#### B. Framework Manager Client (`WellnessManager.java`)
Exposes public/system APIs to system applications like the native camera:
```java
// frameworks/base/core/java/android/wellness/WellnessManager.java
public class WellnessManager {
    private final IWellnessManager mService;

    public WellnessManager(Context context, IWellnessManager service) {
        mService = service;
    }

    @RequiresPermission(android.Manifest.permission.MANAGE_WELLNESS_INTELLIGENCE)
    public void analyzeFrame(HardwareBuffer buffer, SceneCallback callback) {
        // Direct zero-copy graphic buffer transfer via Binder
        mService.analyzeHardwareBuffer(buffer, callback);
    }
}
```

---

### 3. SELinux Security Policies (`SEPolicy`)

To permit IPC between the camera app, system server, and hardware neural accelerators without disabling SELinux, the following policy rules are required:

#### `sepolicy/private/service_contexts`
```text
wellness_service          u:object_r:wellness_service:s0
```

#### `sepolicy/private/service.te`
```text
type wellness_service, app_api_service, system_server_service, service_manager_type;
```

#### `sepolicy/private/system_server.te`
```text
# Allow system_server to register and manage the wellness service
allow system_server wellness_service:service_manager { add find };

# Allow system_server to interact with Qualcomm NPU / Hexagon DSP
allow system_server qdsp_device:chr_file { read write ioctl open };
allow system_server ion_device:chr_file { read open ioctl };
```

#### `sepolicy/private/cameraserver.te` / `priv_app.te`
```text
# Allow native iQOO Camera app to find and bind to wellness_service
allow priv_app wellness_service:service_manager find;
allow priv_app system_server:binder { call transfer };
```

---

### 4. Zero-Copy HardwareBuffer Frame Transfer
In the prototype, `ImageProxy` frames are analyzed in JVM memory.  
In native OriginOS production, frames are passed via **`android.hardware.HardwareBuffer`** over Binder. This avoids expensive memory copies between the camera process and `system_server`, allowing inference directly against GPU/NPU memory mapped buffers.
