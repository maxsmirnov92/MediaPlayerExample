package ru.maxsmr.commonutils.android.gui.views;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public class RecyclerScrollableController extends RecyclerView.OnScrollListener {
    public static final int VISIBLE_THRESHOLD = 5;

    private int previousTotal = 0;
    private boolean loading = true;

    private OnLastItemVisibleListener listener;

    public RecyclerScrollableController(OnLastItemVisibleListener listener) {
        if (listener == null) {
            listener = OnLastItemVisibleListener.STUB;
        }
        this.listener = listener;
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        int visibleItemCount = recyclerView.getChildCount();
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int totalItemCount = layoutManager.getItemCount();
        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();

        if (loading) {
            if (totalItemCount > previousTotal) {
                loading = false;
                previousTotal = totalItemCount;
            }
        }
        boolean isLastItemVisible = totalItemCount - visibleItemCount <= firstVisibleItem + VISIBLE_THRESHOLD;
        if (!loading && isLastItemVisible) {
            if (listener != null) {
                listener.onLastItemVisible();
            }
            loading = true;
        }
    }

    public interface OnLastItemVisibleListener {

        OnLastItemVisibleListener STUB = new OnLastItemVisibleListener() {
            @Override
            public void onLastItemVisible() {
            }
        };

        void onLastItemVisible();
    }
}
