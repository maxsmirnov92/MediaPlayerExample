package ru.maxsmr.commonutils.android.gui.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import ru.maxsmr.commonutils.android.gui.GuiUtils;
import ru.maxsmr.commonutils.data.CompareUtils;
import ru.maxsmr.commonutils.graphic.GraphicUtils;

public class MaskClickLayout extends FrameLayout {

    private static final Logger logger = LoggerFactory.getLogger(MaskClickLayout.class);

    private ImageView backgroundImageView;
    private ImageView layersImageView;
    private ImageView masksImageView;
    private ClickMask clickMask;

    @NonNull
    private final LinkedList<ItemHitCallback> itemHitCallbacks = new LinkedList<>();

    @NonNull
    private final LinkedList<LayoutChangeListener> layoutChangeListeners = new LinkedList<>();

    @NonNull
    private ChoiceMode choiceMode = ChoiceMode.MULTIPLE;

    @NonNull
    private final LinkedHashSet<Item> selectedItems = new LinkedHashSet<>();

    public MaskClickLayout(Context context) {
        super(context);
        init();
    }

    public MaskClickLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MaskClickLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MaskClickLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        addView(backgroundImageView = new ImageView(getContext()));
        addView(masksImageView = new ImageView(getContext()));
        addView(layersImageView = new ImageView(getContext()));
    }

    public void addItemHitCallback(@NonNull ItemHitCallback c) {
        if (!itemHitCallbacks.contains(c)) {
            itemHitCallbacks.add(c);
        }
    }

    public void removeItemHitCallback(@NonNull ItemHitCallback c) {
        if (itemHitCallbacks.contains(c)) {
            itemHitCallbacks.remove(c);
        }
    }

    @NonNull
    public static List<String> getTags(@NonNull Collection<Item> items) {
        List<String> tags = new ArrayList<>();
        for (Item item : items) {
            if (item != null) {
                tags.add(item.getTag());
            }
        }
        return tags;
    }

    @NonNull
    public ChoiceMode getChoiceMode() {
        return choiceMode;
    }

    public void setChoiceMode(@NonNull ChoiceMode mode) {
        if (this.choiceMode != mode) {
            this.choiceMode = mode;
            if (isLoaded()) {
                if (this.choiceMode == ChoiceMode.NONE || (this.choiceMode == ChoiceMode.SINGLE && getSelectedItemsCount() > 1))
                    clearSelection();
            }
            dispatchChoiceModeChanged();
        }
    }

    public int getSelectedItemsCount() {
        return selectedItems.size();
    }

    @NonNull
    public LinkedHashSet<Item> getSelectedItems() {
        return new LinkedHashSet<>(selectedItems);
    }

    @NonNull
    public LinkedHashSet<String> getSelectedItemsTags() {
        return new LinkedHashSet<>(getTags(getSelectedItems()));
    }

    public boolean isItemSelected(Item item) {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        return selectedItems.contains(item);
    }

    public boolean setItemSelected(@NonNull Item item, boolean isSelected) {
        logger.debug("setItemSelected(), item=" + item + ", isSelected=" + isSelected);

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        if (choiceMode != ChoiceMode.NONE) {

            boolean isAlreadySelected = isItemSelected(item);
            if (isSelected) {
                if (!isAlreadySelected && choiceMode == ChoiceMode.SINGLE) {
                    selectedItems.clear();
                }
                selectedItems.add(item);
            } else {
                selectedItems.remove(item);
            }

            if (isSelected ^ isAlreadySelected) {

                List<Drawable> drawables = new ArrayList<>();

                switch (choiceMode) {

                    case MULTIPLE:
                        for (Item selectedItem : selectedItems) {
                            drawables.add(new BitmapDrawable(getContext().getResources(), selectedItem.layerPair.second));
                        }
                        break;

                    default:
                        break;
                }

                if (isSelected) {
                    drawables.add(new BitmapDrawable(getContext().getResources(), item.layerPair.second));
                }

                LayerDrawable layerDrawable = !drawables.isEmpty() ? new LayerDrawable(drawables.toArray(new Drawable[drawables.size()])) : null;
                layersImageView.setImageDrawable(layerDrawable);

                dispatchSelectionChanged(item, isSelected);
            }

            return true;
        }

        return false;
    }

    public boolean setItemsSelected(@Nullable Collection<Item> items, boolean isSelected) {
        boolean success = false;
        if (items != null) {
            success = true;
            for (Item it : items) {
                if (!setItemSelected(it, isSelected)) {
                    success = false;
                }
            }
        }
        return success;
    }

    public boolean toggleItemSelected(@NonNull Item item) {
        logger.debug("toggleItemSelected(), item=" + item);
        return setItemSelected(item, !isItemSelected(item));
    }

    public boolean toggleItemsSelected(@Nullable Collection<Item> items) {
        boolean success = true;
        if (items != null) {
            for (Item it : items) {
                if (!toggleItemSelected(it)) {
                    success = false;
                }
            }
        }
        return success;
    }

    @Nullable
    public Item findItemByTag(@Nullable String tag) {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        for (Item item : clickMask.items) {
            if (item != null && CompareUtils.stringsEqual(item.tag, tag, false)) {
                return item;
            }
        }

        return null;
    }

    @NonNull
    public List<Item> findItemsByTags(@Nullable Collection<String> tags) {
        List<Item> items = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                Item item = findItemByTag(tag);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }


    public boolean isItemSelectedByTag(@Nullable String tag) {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        Item item = findItemByTag(tag);
        return item != null && isItemSelected(item);
    }

    public boolean setItemSelectedByTag(@Nullable String tag, boolean isSelected) {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        Item item = findItemByTag(tag);
        return item != null && setItemSelected(item, isSelected);
    }

    public boolean setItemsSelectedByTags(@Nullable Collection<String> tags, boolean isSelected) {

        boolean success = false;

        if (tags != null) {
            success = true;
            for (String tag : tags) {
                if (!setItemSelectedByTag(tag, isSelected)) {
                    success = false;
                }
            }
        }

        return success;
    }

    public boolean toggleItemSelectedByTag(@Nullable String tag) {
        return setItemSelectedByTag(tag, !isItemSelectedByTag(tag));
    }

    public boolean toggleItemsSelectedByTags(@Nullable List<String> tags) {
        boolean success = true;
        if (tags != null) {
            for (String tag : tags) {
                if (!toggleItemSelectedByTag(tag)) {
                    success = false;
                }
            }
        }
        return success;
    }

    public void clearSelection() {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        if (getSelectedItemsCount() > 0) {
            Iterator<Item> iterator = selectedItems.iterator();
            while (iterator.hasNext()) {
                Item it = iterator.next();
                iterator.remove();
                dispatchSelectionChanged(it, false);
            }
            layersImageView.setImageDrawable(null);
        }
    }

    public void addSelectionChangeListener(@NonNull LayoutChangeListener l) {
        if (!layoutChangeListeners.contains(l)) {
            layoutChangeListeners.add(l);
        }
    }

    public void removeSelectionChangeListener(@NonNull LayoutChangeListener l) {
        if (layoutChangeListeners.contains(l)) {
            layoutChangeListeners.remove(l);
        }
    }

    @NonNull
    public LinkedHashSet<Item> getItems() {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        return clickMask.getItems();
    }

    @NonNull
    public LinkedHashSet<String> getItemsTags() {
        return new LinkedHashSet<>(getTags(getItems()));
    }


    public void drawMask(int id) {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        for (Item it : clickMask.items) {
            if (it.maskedPair.first == id) {
                LayerDrawable masksDrawable = getMasks();
                List<Drawable> drawables = new ArrayList<>();
                if (masksDrawable != null) {
                    for (int i = 0; i < masksDrawable.getNumberOfLayers(); i++) {
                        drawables.add(masksDrawable.getDrawable(i));
                    }
                }
                drawables.add(new BitmapDrawable(getContext().getResources(), it.maskedPair.second));
                masksDrawable = new LayerDrawable(drawables.toArray(new Drawable[drawables.size()]));
                masksImageView.setImageDrawable(masksDrawable);
            }
        }
    }

    public void clearAllMasks() {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        masksImageView.setImageDrawable(null);
    }

    public void drawAllMasks() {

        if (!isLoaded()) {
            throw new IllegalStateException("clickMask was not loaded");
        }

        List<Drawable> drawables = new ArrayList<>();
        for (Item it : clickMask.items) {
            drawables.add(new BitmapDrawable(getContext().getResources(), it.maskedPair.second));
        }
        LayerDrawable masksDrawable = new LayerDrawable(drawables.toArray(new Drawable[drawables.size()]));
        masksImageView.setImageDrawable(masksDrawable);
    }

    private boolean dispatchItemPreHit(@NonNull Point clickPoint, @NonNull Item item) {
        boolean b = true;
        synchronized (itemHitCallbacks) {
            for (ItemHitCallback c : itemHitCallbacks) {
                if (!c.onItemPreHit(clickPoint, item)) {
                    b = false;
                }
            }
        }
        return b;
    }

    private boolean dispatchItemsHit(@NonNull Point clickPoint, @NonNull List<Item> items) {
        boolean b = true;
        synchronized (itemHitCallbacks) {
            for (ItemHitCallback c : itemHitCallbacks) {
                if (!c.onItemsHit(clickPoint, items)) {
                    b = false;
                }
            }
        }
        return b;
    }

    private void dispatchChoiceModeChanged() {
        synchronized (layoutChangeListeners) {
            for (LayoutChangeListener l : layoutChangeListeners) {
                l.onChoiceModeChanged(choiceMode);
            }
        }
    }

    private void dispatchSelectionChanged(@NonNull Item item, boolean isSelected) {
        synchronized (layoutChangeListeners) {
            for (LayoutChangeListener l : layoutChangeListeners) {
                l.onSelectedChanged(item, isSelected);
            }
        }
    }

    private static void correctImageViewSize(@NonNull ImageView v) {
        logger.debug("correctImageViewSize(), v=" + v);
        Pair<Integer, Integer> viewSize = GuiUtils.getRescaledImageViewSize(v);
        v.setMaxWidth(viewSize.first);
        v.setMaxHeight(viewSize.second);
        v.invalidate();
        v.requestLayout();
        logger.debug("measured size: " + v.getMeasuredWidth() + "x" + v.getMeasuredHeight());
        logger.debug("drawable size: " + GuiUtils.getImageViewDrawableSize(v).first + "x" + GuiUtils.getImageViewDrawableSize(v).second);
        logger.debug("rescaled size: " + GuiUtils.getRescaledImageViewSize(v).first + "x" + GuiUtils.getRescaledImageViewSize(v).second);
    }

    public void setBackgroundVisibility(boolean visibility) {
        backgroundImageView.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    public void setMasksVisibility(boolean visibility) {
        masksImageView.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    public void setLayersVisibility(boolean visibility) {
        layersImageView.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    @Nullable
    public Drawable getBackground() {
        return backgroundImageView.getDrawable();
    }

    @Nullable
    public LayerDrawable getLayers() {
        Drawable layers = layersImageView.getDrawable();
        return layers instanceof LayerDrawable ? (LayerDrawable) layersImageView.getDrawable() : null;
    }

    @Nullable
    public LayerDrawable getMasks() {
        Drawable layers = masksImageView.getDrawable();
        return layers instanceof LayerDrawable ? (LayerDrawable) masksImageView.getDrawable() : null;
    }

    @NonNull
    public Pair<Integer, Integer> getBackgroundImageSize() {
        return GuiUtils.getImageViewDrawableSize(backgroundImageView);
    }

    @NonNull
    public Pair<Integer, Integer> getLayersImageSize() {
        return GuiUtils.getImageViewDrawableSize(layersImageView);
    }

    @NonNull
    public Pair<Integer, Integer> getMasksImageSize() {
        return GuiUtils.getImageViewDrawableSize(masksImageView);
    }

    @NonNull
    public Pair<Integer, Integer> getBackgroundRescaledSize() {
        return GuiUtils.getRescaledImageViewSize(backgroundImageView);
    }

    @NonNull
    public Pair<Integer, Integer> getLayersRescaledSize() {
        return GuiUtils.getRescaledImageViewSize(layersImageView);
    }

    @NonNull
    public Pair<Integer, Integer> getMasksRescaledSize() {
        return GuiUtils.getRescaledImageViewSize(masksImageView);
    }

    public boolean isLoaded() {
        return clickMask != null;
    }

    @Nullable
    public ClickMask getClickMask() {
        return clickMask;
    }

    public void load(ClickMask clickMask) {
        logger.debug("load(), clickMask=" + clickMask);

        if (clickMask != null) {

            unload();

            this.clickMask = clickMask;

            Bitmap background = GraphicUtils.createBitmapFromResource(clickMask.backgroundResId, 1, getContext());

            if (background == null) {
                throw new RuntimeException("background bitmap was not created");
            }

            if (clickMask.options.scaleToParent) {
                DisplayMetrics metrics = new DisplayMetrics();
                ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
                Bitmap scaledBackground = GraphicUtils.createResizedBitmap(background, metrics.widthPixels);
                background.recycle();
                background = scaledBackground;
            }

            if (clickMask.options.rotateAngleCorrect()) {
                background = GraphicUtils.rotateBitmap(background, clickMask.options.rotateAngle, true);
            }

            if (clickMask.options.mirror) {
                background = GraphicUtils.mirrorBitmap(background, true);
            }

            this.backgroundImageView.setImageBitmap(background);

//        if (addLayers) {
//            Drawable[] layers = new Drawable[clickMask.items.size()];
//            int i = 0;
//            for (Item item : clickMask.items) {
////                if (item.options.rotateAngleCorrect() || item.options.mirror) {
////                    if (item.options.rotateAngleCorrect()) {
////                        layers[i] = new BitmapDrawable(getContext().getResources(), item.maskedPair.second);
////                    }
////                } else {
////                    layers[i] = ContextCompat.getDrawable(getContext(), clickMask.items.get(i).layerResId);
////                }
//                layers[i] = new BitmapDrawable(getContext().getResources(), item.maskedPair.second);
//                i++;
//            }
//            LayerDrawable layerDrawable = new LayerDrawable(layers);
//            this.layersImageView.setImageDrawable(layerDrawable);
//        }

        }
    }

    public void unload() {
        logger.debug("unload()");

        if (isLoaded()) {

            clearSelection();

            backgroundImageView.setImageDrawable(null);
            layersImageView.setImageDrawable(null);
            masksImageView.setImageDrawable(null);

            for (Item i : clickMask.items) {
                if (!i.layerPair.second.isRecycled()) {
                    i.layerPair.second.recycle();
                }
                if (!i.maskedPair.second.isRecycled()) {
                    i.maskedPair.second.recycle();
                }
            }
            clickMask.items.clear();
            clickMask = null;
        }
    }

    @NonNull
    private Point correctCoordsBySize(@NonNull Point p, @NonNull Pair<Integer, Integer> size, @NonNull Pair<Integer, Integer> targetSize) {
        float scalerX = (float) p.x / (float) size.first;
        float scalerY = (float) p.y / (float) size.second;
        return new Point(Math.round((float) targetSize.first * scalerX), Math.round((float) targetSize.second * scalerY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (clickMask != null) {
            if (event.getPointerCount() == 1) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    Pair<Integer, Integer> backgroundSize = getBackgroundImageSize();

                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    logger.debug("source point {x=" + x + ", y=" + y + "}");

                    Point point = correctCoordsBySize(new Point(x, y), new Pair<>(backgroundImageView.getMeasuredWidth(), backgroundImageView.getMeasuredHeight()), new Pair<>(backgroundSize.first, backgroundSize.second));
                    point.set(x = point.x, y = point.y);
                    logger.debug("corrected point {x=" + x + ", y=" + y + "}");

                    logger.debug("[background] imageView: " + backgroundImageView.getMeasuredWidth() + "x" + backgroundImageView.getMeasuredHeight());
                    logger.debug("[background] drawable: " + backgroundSize.first + "x" + backgroundSize.second);

//                List<Drawable> drawables = new ArrayList<>();

                    List<Item> hitItems = new ArrayList<>();

                    for (Item item : clickMask.items) {

                        Bitmap maskBitmap = item.maskedPair.second; // GraphicUtils.createBitmapFromResource(item.maskResId, 1, getContext());

                        if (item.options.scaleToParent) {
                            if (backgroundSize.first > 0 && backgroundSize.second > 0) {
                                logger.debug("[mask] scaling to " + backgroundSize.first + "x" + backgroundSize.second + "...");
                                Bitmap scaledMaskBitmap = Bitmap.createScaledBitmap(maskBitmap, backgroundSize.first, backgroundSize.second, false);

                                if (scaledMaskBitmap == null) {
                                    throw new RuntimeException("could not create scaled bitmap: " + backgroundSize.first + "x" + backgroundSize.second);
                                }

                                maskBitmap.recycle();
                                maskBitmap = scaledMaskBitmap;
                            }
                        }

                        logger.debug("[mask] maskBitmap: " + maskBitmap.getWidth() + "x" + maskBitmap.getHeight());

                        int color = maskBitmap.getPixel(x, y);
                        color &= ~0xFF000000;
                        logger.debug("clicked color: " + Integer.toHexString(color) + ", mask color: " + Integer.toHexString(item.maskClickColor) + ", " + "(id=" + item.maskedPair.first + ")");

                        if (color == item.maskClickColor) {

                            if (dispatchItemPreHit(point, item)) {
                                continue;
                            }

                            hitItems.add(item);

//                          Drawable currentDrawable = layersImageView.getDrawable();
//                          final int layersCount = currentDrawable instanceof LayerDrawable ? ((LayerDrawable) currentDrawable).getNumberOfLayers() : 0;

                            if (clickMask.findFirstMatch) {
                                break;
                            }
                        }
//                  maskBitmap.recycle();
                    }

                    if (!hitItems.isEmpty()) {
                        if (!dispatchItemsHit(point, hitItems)) {
                            for (Item item : hitItems) {
                                if (!isItemSelected(item)) {
                                    setItemSelected(item, true);
                                } else {
                                    toggleItemSelected(item);
                                }
                            }
                        }
                    }

//                LayerDrawable layerDrawable = !drawables.isEmpty() ? new LayerDrawable(drawables.toArray(new Drawable[drawables.size()])) : null;
//                layersImageView.setImageDrawable(layerDrawable);
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unload();
    }

    public static class TransformOptions implements Serializable {

        public final boolean scaleToParent;

        public final int rotateAngle;

        public final boolean mirror;

        public TransformOptions(boolean scaleToParent, int rotateAngle, boolean mirror) {
            this.scaleToParent = scaleToParent;
            this.rotateAngle = rotateAngle;
            this.mirror = mirror;
        }

        public boolean rotateAngleCorrect() {
            return rotateAngle > 0 && rotateAngle < 360;
        }

        @Override
        public String toString() {
            return "TransformOptions{" +
                    "scaleToParent=" + scaleToParent +
                    ", rotateAngle=" + rotateAngle +
                    ", mirror=" + mirror +
                    '}';
        }
    }

    public static class Item {

        @Nullable
        private String tag;

        @NonNull
        private Pair<Integer, Bitmap> layerPair;

        @NonNull
        private Pair<Integer, Bitmap> maskedPair;

        @NonNull
        public Pair<Integer, Bitmap> getLayerPair() {
            return new Pair<>(layerPair.first, layerPair.second);
        }

        @NonNull
        public Pair<Integer, Bitmap> getMaskedPair() {
            return new Pair<>(maskedPair.first, maskedPair.second);
        }

        @ColorInt
        public final int maskClickColor;

        @NonNull
        public final TransformOptions options;

        public Item(@NonNull Context context, @Nullable String tag, @DrawableRes int layerResId, @DrawableRes int maskResId, @ColorInt int maskClickColor, @NonNull TransformOptions options) throws RuntimeException {

            this.tag = tag;

            Bitmap layerBitmap = GraphicUtils.createBitmapFromResource(layerResId, 1, context);
            if (options.rotateAngleCorrect()) {
                layerBitmap = GraphicUtils.rotateBitmap(layerBitmap, options.rotateAngle, true);
            }
            if (options.mirror) {
                layerBitmap = GraphicUtils.mirrorBitmap(layerBitmap, true);
            }
            if (layerBitmap == null) {
                throw new RuntimeException("layer bitmap was not created");
            }
            this.layerPair = new Pair<>(layerResId, layerBitmap);

            Bitmap maskBitmap = GraphicUtils.createBitmapFromResource(maskResId, 1, context);
            if (options.rotateAngleCorrect()) {
                maskBitmap = GraphicUtils.rotateBitmap(maskBitmap, options.rotateAngle, true);
            }
            if (options.mirror) {
                maskBitmap = GraphicUtils.mirrorBitmap(maskBitmap, true);
            }
            if (maskBitmap == null) {
                throw new RuntimeException("mask bitmap was not created");
            }
            this.maskedPair = new Pair<>(maskResId, maskBitmap);

            this.maskClickColor = maskClickColor & ~0xFF000000;
            this.options = options;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Item item = (Item) o;

            if (maskClickColor != item.maskClickColor) return false;
            if (tag != null ? !tag.equals(item.tag) : item.tag != null) return false;
            if (!layerPair.equals(item.layerPair)) return false;
            if (!maskedPair.equals(item.maskedPair)) return false;
            return options.equals(item.options);

        }

        @Override
        public int hashCode() {
            int result = tag != null ? tag.hashCode() : 0;
            result = 31 * result + layerPair.hashCode();
            result = 31 * result + maskedPair.hashCode();
            result = 31 * result + maskClickColor;
            result = 31 * result + options.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "tag=" + tag +
                    ", layerPair=" + layerPair +
                    ", maskedPair=" + maskedPair +
                    ", maskClickColor=" + maskClickColor +
                    ", options=" + options +
                    '}';
        }
    }

    public static class ClickMask {

        @DrawableRes
        public final int backgroundResId;

        public final boolean findFirstMatch;

        @NonNull
        public final TransformOptions options;

        @NonNull
        private final LinkedHashSet<Item> items = new LinkedHashSet<>();

        @NonNull
        public LinkedHashSet<Item> getItems() {
            return new LinkedHashSet<>(items);
        }

        public ClickMask(@DrawableRes int backgroundResId, boolean findFirstMatch, @NonNull TransformOptions options, Item... items) {
            this.backgroundResId = backgroundResId;
            this.findFirstMatch = findFirstMatch;
            this.options = options;
            if (items != null) {
                this.items.addAll(Arrays.asList(items));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClickMask clickMask = (ClickMask) o;

            if (backgroundResId != clickMask.backgroundResId) return false;
            if (findFirstMatch != clickMask.findFirstMatch) return false;
            if (!options.equals(clickMask.options)) return false;
            return items.equals(clickMask.items);

        }

        @Override
        public int hashCode() {
            int result = backgroundResId;
            result = 31 * result + (findFirstMatch ? 1 : 0);
            result = 31 * result + options.hashCode();
            result = 31 * result + items.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ClickMask{" +
                    "backgroundResId=" + backgroundResId +
                    ", findFirstMatch=" + findFirstMatch +
                    ", options=" + options +
                    ", items=" + items +
                    '}';
        }
    }

    public enum ChoiceMode {
        NONE, SINGLE, MULTIPLE
    }

    public interface LayoutChangeListener {

        void onChoiceModeChanged(@NonNull ChoiceMode mode);

        void onSelectedChanged(@NonNull Item item, boolean isSelected);
    }

    public interface ItemHitCallback {

        /**
         * @return true if event consumed
         */
        boolean onItemPreHit(@NonNull Point clickPoint, @NonNull Item item);

        /**
         * @return true if event consumed
         */
        boolean onItemsHit(@NonNull Point clickPoint, @NonNull List<Item> items);
    }

}
