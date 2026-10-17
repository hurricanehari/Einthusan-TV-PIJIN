# Elil TV — Brought by PIJIN | v0.9.6

Based on v0.9.5. Updates only the native interface (`MainActivity.java`) and version values (`app/build.gradle`).

- Featured: up to eight catalogued films; automatic rotation and remote-accessible previous / next.
- Homepage: no Explore buttons; category headings open complete collections, each row loads extra listing pages at its right edge.
- Movies: category tabs (All Movies, Recently Added, Popular, Featured, Staff Picks, Ultra HD, Collections); quality and language filters. Ultra HD scans **labelled** films discovered in available listing pages; not a guarantee of exhaustive results or stream eligibility.
- Sidebar: compact icon rail with expanded navigation, fixed signed-in footer.
- Settings: flat selectable rows for account, profiles, language, history, updates, and About.
- PlayerActivity, authentication, ViewingStore and UpdateActivity remain unmodified from v0.9.5.

Build with GitHub Actions and the original `ELIL_KEYSTORE_BASE64`, `ELIL_KEYSTORE_PASSWORD`, `ELIL_KEY_PASSWORD` secrets. Do not commit the signing keystore. Maintain application ID `com.example.einthusantv` to update in place.

App signing and device-level behaviour must be verified on an actual Fire TV. App uses a temporary legacy target SDK lint exemption for private sideloading; evaluate Android target SDK upgrade separately.
