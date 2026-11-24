MOON TIME — Android/PWA starter

Features in this build:
- Referral-code registration gate. Current demo code: MOON-2026
- Local profile saved on this device (browser/WebView localStorage)
- Create local groups and channels; their metadata and chat messages persist locally
- Existing manual WebRTC peer-to-peer pairing flow retained
- Android WebView wrapper and GitHub Actions workflow to build a debug APK

IMPORTANT LIMITATIONS
1. The invite code is embedded in client-side code. It is a demo gate only, not secure access control; anyone who inspects the app can discover it. Real invitation enforcement requires a trusted backend or signed one-time invitation tokens.
2. Profiles, groups, channels, and messages are local to this device. They do not sync to other users. Group/channel creation here is local UI/data only, not a functioning multi-user group service.
3. WebRTC pairing still requires manually exchanging offer/answer codes. With no STUN/TURN services, direct connection may fail on many mobile networks/NATs.
4. No real account recovery, push notifications, voice/video calls, or production-grade end-to-end identity verification.
5. This source is an independent implementation; it does not copy proprietary source code from Eitaa, Bale, or Rubika APKs.

BUILD APK WITH GITHUB
- Upload/push the contents of this folder to the repository's main branch.
- Open the repository's Actions tab and run "Build Moon Time APK" (or push to main).
- When the workflow succeeds, open its run and download the artifact named moon-time-debug-apk. It contains app-debug.apk.
