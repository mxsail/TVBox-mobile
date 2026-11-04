package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.CategoryChipAdapter;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.interfaces.OnSelectListener;

import java.util.List;

/**
 * 首页「展开全部分类」弹窗: 底部弹出 M3 chips 网格, 当前分类高亮,
 * 点选后经 OnSelectListener 回调 index 由 HomeFragment 切换 tab。
 */
public class CategoryDropdownDialog extends BottomPopupView {

    private final List<String> names;
    private final int current;
    private final OnSelectListener listener;

    public CategoryDropdownDialog(@NonNull Context context, List<String> names, int current, OnSelectListener listener) {
        super(context);
        this.names = names;
        this.current = current;
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_category_dropdown;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        RecyclerView rv = findViewById(R.id.rv_categories);
        com.github.tvbox.osc.view.BounceOverScroll.attach(rv);
        rv.setLayoutManager(new GridLayoutManager(getActivity(), 4));
        CategoryChipAdapter adapter = new CategoryChipAdapter(position -> {
            dismissWith(() -> {
                if (listener != null) listener.onSelect(position, names.get(position));
            });
        });
        rv.setAdapter(adapter);
        adapter.setData(names, current);
        // 高度按行数算(chip 40dp+margin 8dp≈48dp/行), 封顶 7 行约半屏, 多则内部滚动
        float density = getActivity().getResources().getDisplayMetrics().density;
        int rows = (int) Math.ceil(names.size() / 4.0);
        int rowH = (int) (48 * density);
        int h = Math.min(rows, 7) * rowH + (int) (12 * density);
        ViewGroup.LayoutParams lp = rv.getLayoutParams();
        lp.height = h;
        rv.setLayoutParams(lp);
    }
}
