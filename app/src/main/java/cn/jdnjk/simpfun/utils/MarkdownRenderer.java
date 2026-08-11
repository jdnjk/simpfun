package cn.jdnjk.simpfun.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.AsyncDrawableScheduler;
import io.noties.markwon.image.DrawableUtils;
import io.noties.markwon.image.ImageSizeResolverDef;
import io.noties.markwon.image.ImageSpanFactory;
import io.noties.markwon.linkify.LinkifyPlugin;

public final class MarkdownRenderer {

    private static final Object LOCK = new Object();
    private static MarkdownRenderer instance;
    private static volatile Executor executor;

    private final Markwon markwon;

    private MarkdownRenderer(@NonNull Context context) {
        Context app = context.getApplicationContext();
        this.markwon = Markwon.builder(app)
                .usePlugin(CorePlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(TablePlugin.create(app))
                .usePlugin(TaskListPlugin.create(app))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.imageSizeResolver(new ImageSizeResolverDef());
                    }
                })
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                        builder.setFactory(org.commonmark.node.Image.class, new ImageSpanFactory());
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.asyncDrawableLoader(new Glide5AsyncDrawableLoader(app));
                    }

                    @Override
                    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
                        AsyncDrawableScheduler.unschedule(textView);
                    }

                    @Override
                    public void afterSetText(@NonNull TextView textView) {
                        AsyncDrawableScheduler.schedule(textView);
                    }
                })
                .build();
    }

    public static MarkdownRenderer getInstance(@NonNull Context context) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new MarkdownRenderer(context);
            }
            return instance;
        }
    }

    private static MarkdownRenderer instance() {
        if (instance == null) {
            throw new IllegalStateException("MarkdownRenderer 尚未初始化，请先调用 getInstance()");
        }
        return instance;
    }

    private static Executor getExecutor() {
        Executor e = executor;
        if (e == null) {
            synchronized (LOCK) {
                e = executor;
                if (e == null) {
                    e = Executors.newSingleThreadExecutor();
                    executor = e;
                }
            }
        }
        return e;
    }

    private static final AtomicLong REQUEST_ID = new AtomicLong();

    /**
     * 将 Markdown 文本渲染到 TextView。
     * <p>
     * 会自动设置 {@link LinkMovementMethod} 使链接可点击。
     *
     * @param textView 目标 TextView
     * @param markdown 原始 Markdown 字符串，可能包含 base64 图片
     */
    public void render(@NonNull TextView textView, String markdown) {
        String safe = preprocess(markdown);
        // 使用 markwon.setMarkdown 而不是 setText(toMarkdown(...))，
        // 这样 AsyncDrawableScheduler 才会调度图片异步加载。
        markwon.setMarkdown(textView, safe);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setLinksClickable(true);
    }

    /**
     * 解除 TextView 上残留的异步图片调度。
     * <p>
     * 当同一 View 从 Markdown 行复用为纯文本行时调用，避免旧图片继续加载/显示。
     */
    public static void clear(@NonNull TextView textView) {
        // 作废在途的异步渲染（清空单参 tag），再解除图片调度。
        // 否则 View 被复用为纯文本后，后台解析完成的旧 markdown 仍会 setText 覆盖。
        textView.setTag(null);
        AsyncDrawableScheduler.unschedule(textView);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * 异步渲染 Markdown：后台线程完成预处理与解析（正则 + commonmark），主线程仅 setText。
     * <p>
     * 用于 RecyclerView bind() 等主线程路径，避免超大 base64 / 复杂 HTML 拖垮主线程。
     */
    public static void renderAsync(@NonNull TextView textView, String markdown) {
        if (instance == null) {
            getInstance(textView.getContext());
        }
        long token = REQUEST_ID.incrementAndGet();
        textView.setTag(token);
        Executor exec = getExecutor();
        exec.execute(() -> {
            Spanned spanned = null;
            String fallback = markdown;
            try {
                String safe = preprocess(markdown);
                fallback = safe;
                //Log.d("MarkdownRenderer", "parsing: " + safe);
                spanned = instance().markwon.toMarkdown(safe);
                //Log.d("MarkdownRenderer", "parsed ok, chars=" + spanned.length());
            } catch (Exception e) {
                // 解析失败（畸形表格/HTML/正则）时静默回退为纯文本，保证内容可见
                Log.e("MarkdownRenderer", "toMarkdown failed", e);
                spanned = null;
            }
            final Spanned parsed = spanned;
            final String plain = fallback;
            MAIN_HANDLER.post(() -> {
                if (!Objects.equals(textView.getTag(), token)) return;
                AsyncDrawableScheduler.unschedule(textView);
                if (parsed != null) {
                    textView.setText(parsed);
                } else {
                    textView.setText(plain);
                }
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setLinksClickable(true);
                if (parsed != null) {
                    AsyncDrawableScheduler.schedule(textView);
                }
            });
        });
    }

    /**
     * 防御性预处理：
     * <ul>
     *   <li>若整段文本就是一个 data: URI，则包装成 Markdown 图片语法。</li>
     *   <li>移除未被 {@code ![...](...)} 包裹的裸 base64 数据 URI，防止超长字符串进入 TextView。</li>
     * </ul>
     */
    private static String preprocess(String markdown) {
        if (markdown == null) {
            return "";
        }
        String trimmed = markdown.trim();

        // 如果整个字段就是一个 data: URI，包装成图片
        if (trimmed.startsWith("data:image/") && !trimmed.contains("\n")) {
            return "![图片](" + trimmed + ")";
        }

        // 移除未嵌入 Markdown 图片语法的裸 base64 数据 URI（长度 >= 100 才被认为是图片）
        // 保留 Markdown 图片语法中的 data: URI：(?<!\]\() 避免替换 ![alt](data:...) 里的 URL
        return markdown.replaceAll("(?<!\\]\\()data:image/[^\\s;]+;base64,[A-Za-z0-9+/=]{100,}", "[图片]");
    }

    /**
     * 直接调用 Glide 5 加载图片，支持 data: URI 与网络 URL。
     */
    private static final class Glide5AsyncDrawableLoader extends AsyncDrawableLoader {

        private final Context context;

        Glide5AsyncDrawableLoader(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void load(@NonNull AsyncDrawable drawable) {
            String destination = drawable.getDestination();
            Glide.with(context)
                    .load(destination)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            if (drawable.isAttached()) {
                                DrawableUtils.applyIntrinsicBoundsIfEmpty(resource);
                                drawable.setResult(resource);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            if (drawable.isAttached()) {
                                drawable.clearResult();
                            }
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            drawable.clearResult();
                        }
                    });
        }

        @Override
        public void cancel(@NonNull AsyncDrawable drawable) {
        }

        @Nullable
        @Override
        public Drawable placeholder(@NonNull AsyncDrawable drawable) {
            return null;
        }
    }
}
