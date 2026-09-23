# LunaxyMusic V92.2 — Hero / Reorder Stability Hotfix

V92.2 is based on V92.1 and focuses on two real-device regressions reported after Motion System 2.0.

## 1. Song card → Full Player Hero landing

V92.1 targeted the whole `VinylRecordView` bounds while the actual circular album artwork only occupies the centre of that view (the outer area is reserved for record/wave visuals). That produced an apparent over-grow / one-frame correction when the proxy was replaced by the real record.

V92.2:
- exposes the real artwork diameter from `VinylRecordView`;
- lands the shared artwork on the actual circular artwork bounds, not the outer wave container;
- drives x/y/scale/shape/cross-fade from one `ValueAnimator` progress clock;
- begins revealing the real record before the proxy is removed;
- keeps record rotation held during the landing;
- does not manually recycle the hardware-transition snapshot.

## 2. Long-duration queue / playlist reorder stability

The long-drag path is deliberately reduced to finger geometry + bounded list scrolling.

V92.2:
- keeps Activity-level gesture ownership after long press, even if the original handle scrolls off-screen;
- coalesces reorder-model work to at most one pass per display frame;
- throttles edge auto-scroll to ~24 Hz;
- uses layout-space centres + hysteresis so translated rows cannot move their own hit thresholds;
- opens each destination continuously according to overlap depth instead of instantly creating a full slot;
- broadens the squeeze range for a slower, softer “cards being pushed apart” look;
- suppresses artwork loading while a manual reorder is active; visible covers are rebound once after drop/cancel;
- rejects stale queued ImageLoader jobs before disk decode/network work;
- pins the source row with transient state during the gesture;
- separates persistence, drop animation, modal cleanup and parent-page refresh;
- never explicitly recycles the drag snapshot after hardware animation.

## Stability boundary

Playback URL resolution, PlaybackService, SourceCoordinator, personalization/recommendation logic, ListenBrainz integration, Search Engine V2 and lyric cache behavior are not changed by V92.2. `ImageLoader` changes are limited to stale request cancellation for recycled list rows; cache capacity and media/playback URL isolation are unchanged.

## Real-device priority acceptance

1. Hold a queue/playlist item and drag continuously for 60–120 seconds, including repeated edge auto-scroll. The app must stay alive.
2. Hover slowly around adjacent-row centres. Rows should gradually yield; no large slot should pop open instantly.
3. Drop after a long drag. Ordering should save once, sheet should remain stable, and the parent page should not be rebuilt in the same frame.
4. Open Full Player repeatedly from different song cards. The artwork should grow directly into the visible record artwork without the “large record pauses once” correction.
