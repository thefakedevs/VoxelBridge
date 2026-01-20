# Plan

Goal: Pause Fabric development and focus on common + NeoForge until NeoForge 1.21.1 and 1.21.8 both run reliably. Only then resume Fabric.

1) Freeze Fabric module
- Mark Fabric as inactive in builds and docs.
- Ensure common/neoforge builds and tests do not require Fabric tasks.

2) Audit common/core boundaries for NeoForge-first development
- Identify Fabric-only references inside common and isolate or stub them.
- Define clear platform responsibilities for runtime access, rendering hooks, and resource loading.

3) Align NeoForge 1.21.1 and 1.21.8
- Compare APIs and mappings for both versions.
- Define shared interfaces/config plus version-specific shims.

4) Stabilize NeoForge 1.21.1
- Fix compile/runtime issues.
- Validate build and client launch; verify basic export path.

5) Stabilize NeoForge 1.21.8
- Port differences and fix compile/runtime issues.
- Validate build and client launch; verify basic export path.

6) Cross-version verification
- Add minimal automated checks and logging baselines for 1.21.1 and 1.21.8.

7) Re-enter Fabric (later)
- Re-enable Fabric after NeoForge parity.
- Plan Fabric work using the refined common interfaces.
