# OEM Integration Notes: OriginOS & iQOO Platform Deployment

This directory contains the reference integration architecture for incorporating the iQOO Wellness AI Engine natively into **OriginOS**.

---

### 1. Prototype vs Production Deployment Boundary

| Subsystem | Hackathon Prototype (Today) | OriginOS OEM Production (Tomorrow) |
| :--- | :--- | :--- |
| **Execution Context** | Sandboxed user APK (`com.iqoo.wellness.app`) | Android `system_server` (UID 1000) |
| **Camera Viewfinder** | CameraX inside standalone APK | Native iQOO Camera (`com.android.camera`) |
| **Inter-Process Boundary** | Direct in-process Kotlin calls | Binder IPC via `IWellnessManager.aidl` |
| **Permission Model** | Runtime Android permissions | Signature-level `android.permission.MANAGE_WELLNESS_INTELLIGENCE` |
| **Frame Transfer** | `ImageProxy` JVM buffer | Zero-copy `HardwareBuffer` memory mapped from Camera HAL |
| **Neural Execution** | CPU / Android NNAPI | Qualcomm Neural Processing Engine (QNN) on Hexagon DSP |

---

### 2. OriginOS System Integration Steps

#### Step 1: Framework AIDL Integration
Place `IWellnessManager.aidl` into the framework core:
`frameworks/base/core/java/android/wellness/IWellnessManager.aidl`
Rebuild the Android framework API stubs (`make update-api`).

#### Step 2: System Service Registration
Register `WellnessManagerService` in `SystemServer.java`:
```java
// frameworks/base/services/java/com/android/server/SystemServer.java
ServiceManager.addService("wellness_service", new WellnessManagerService(context));
```

#### Step 3: SELinux Policy Additions
Add the service type and permissions to Android's SEPolicy:
```text
# sepolicy/private/service_contexts
wellness_service          u:object_r:wellness_service:s0

# sepolicy/private/service.te
type wellness_service, app_api_service, system_server_service, service_manager_type;

# sepolicy/private/system_server.te
allow system_server wellness_service:service_manager { add find };
```

#### Step 4: Native Camera Hook (`com.android.camera`)
Inside the stock camera's AI mode coordinator:
```kotlin
val manager = context.getSystemService(Context.WELLNESS_SERVICE) as WellnessManager
manager.analyzeFrame(cameraHalBuffer) { result ->
    cameraViewfinder.renderSmartWellnessHUD(result)
}
```
