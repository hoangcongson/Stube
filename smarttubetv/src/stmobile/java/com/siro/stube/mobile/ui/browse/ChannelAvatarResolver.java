package com.siro.stube.mobile.ui.browse;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves missing feed avatars from the channel page. New YouTube lockup cards carry a
 * channel id but often omit the channel thumbnail; this fills that gap without blocking bind.
 * Requests are deduplicated, limited, and kept independent of Activity lifetimes.
 */
final class ChannelAvatarResolver {
    private static final int MAX_PARALLEL_REQUESTS = 2;
    private static final long RETRY_DELAY_MS = 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final LruCache<String, String> CACHE = new LruCache<>(256);
    private static final Map<String, List<WeakReference<ImageView>>> WAITING = new HashMap<>();
    private static final Map<String, Long> FAILED_AT = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static int sInFlight;

    private ChannelAvatarResolver() { }

    static void load(ImageView view, @Nullable String channelId) {
        if (view == null) {
            return;
        }

        // The general tag is used to reject a late result after RecyclerView recycled this view.
        view.setTag(channelId);
        if (channelId == null || channelId.trim().isEmpty()) {
            showPlaceholder(view);
            return;
        }

        String cached;
        synchronized (LOCK) {
            cached = CACHE.get(channelId);
        }
        if (cached != null) {
            showAvatar(view, cached);
            return;
        }

        // Keep the real channel silhouette visible while the channel lookup runs.
        showPlaceholder(view);
        synchronized (LOCK) {
            List<WeakReference<ImageView>> waiting = WAITING.get(channelId);
            if (waiting == null) {
                waiting = new ArrayList<>();
                WAITING.put(channelId, waiting);
            }
            waiting.add(new WeakReference<>(view));

            long failedAt = FAILED_AT.containsKey(channelId) ? FAILED_AT.get(channelId) : 0L;
            if (!REQUESTED.contains(channelId) && System.currentTimeMillis() - failedAt >= RETRY_DELAY_MS) {
                REQUESTED.add(channelId);
                QUEUE.addLast(channelId);
            }
        }
        startQueuedRequests();
    }

    private static void startQueuedRequests() {
        List<String> starts = new ArrayList<>();
        synchronized (LOCK) {
            while (sInFlight < MAX_PARALLEL_REQUESTS && !QUEUE.isEmpty()) {
                starts.add(QUEUE.removeFirst());
                sInFlight++;
            }
        }
        for (String channelId : starts) {
            requestChannelAvatar(channelId);
        }
    }

    private static void requestChannelAvatar(String channelId) {
        AtomicBoolean completed = new AtomicBoolean(false);
        try {
            ServiceManager manager = YouTubeServiceManager.instance();
            ContentService service = manager.getContentService();
            if (service == null) {
                finish(channelId, null, completed);
                return;
            }

            service.getChannelObserve(channelId).subscribe(
                    groups -> {
                        String url = findChannelAvatar(groups);
                        if (url != null) {
                            finish(channelId, url, completed);
                        }
                    },
                    error -> finish(channelId, null, completed),
                    () -> finish(channelId, null, completed)
            );
        } catch (Throwable ignored) {
            finish(channelId, null, completed);
        }
    }

    @Nullable
    private static String findChannelAvatar(@Nullable List<MediaGroup> groups) {
        if (groups == null) {
            return null;
        }
        for (MediaGroup group : groups) {
            if (group == null || group.getMediaItems() == null) {
                continue;
            }
            for (MediaItem item : group.getMediaItems()) {
                if (item == null) {
                    continue;
                }
                String url = item.getAuthorImageUrl();
                if (isUsable(url)) {
                    return url;
                }
                if (item instanceof MediaItemMetadata) {
                    url = ((MediaItemMetadata) item).getAuthorImageUrl();
                    if (isUsable(url)) {
                        return url;
                    }
                }
                // Channel header items expose their icon as card art, while ordinary video
                // card art is a thumbnail and must never be mistaken for an avatar.
                if (group.getType() == MediaGroup.TYPE_CHANNEL && item.getType() == MediaItem.TYPE_CHANNEL) {
                    url = item.getCardImageUrl();
                    if (isUsable(url)) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isUsable(@Nullable String url) {
        return url != null && !url.trim().isEmpty();
    }

    private static void finish(String channelId, @Nullable String url, AtomicBoolean completed) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }

        List<WeakReference<ImageView>> waiting;
        synchronized (LOCK) {
            waiting = WAITING.remove(channelId);
            REQUESTED.remove(channelId);
            if (isUsable(url)) {
                CACHE.put(channelId, url);
                FAILED_AT.remove(channelId);
            } else {
                FAILED_AT.put(channelId, System.currentTimeMillis());
            }
            sInFlight = Math.max(0, sInFlight - 1);
        }

        if (waiting != null) {
            final String resolvedUrl = url;
            MAIN.post(() -> {
                for (WeakReference<ImageView> reference : waiting) {
                    ImageView imageView = reference.get();
                    if (imageView == null || !channelId.equals(imageView.getTag())) {
                        continue;
                    }
                    if (isUsable(resolvedUrl)) {
                        showAvatar(imageView, resolvedUrl);
                    } else {
                        showPlaceholder(imageView);
                    }
                }
            });
        }
        startQueuedRequests();
    }

    private static void showAvatar(ImageView view, String url) {
        Glide.with(view)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_watch_channel_placeholder)
                .error(R.drawable.ic_watch_channel_placeholder)
                .into(view);
    }

    private static void showPlaceholder(ImageView view) {
        Glide.with(view).clear(view);
        view.setImageResource(R.drawable.ic_watch_channel_placeholder);
    }
}
