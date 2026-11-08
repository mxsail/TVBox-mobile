package com.github.tvbox.osc.ui.dialog;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogAllChannelBinding;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.ui.fragment.LiveFragment;
import com.lxj.xpopup.core.DrawerPopupView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

/**
 * 全频道右侧抽屉: 直播页 Compose 化后不再共享页面 adapter 实例,
 * 这里自建两份 adapter 用 uiState 快照填充, 选中态点击瞬间生效并自动滚动。
 */
public class AllChannelsRightDialog extends DrawerPopupView {

    private final LiveFragment mActivity;
    private com.github.tvbox.osc.databinding.DialogAllChannelBinding mBinding;
    private LiveChannelGroupNewAdapter groupAdapter;
    private LiveChannelItemNewAdapter channelAdapter;

    public AllChannelsRightDialog(@NonNull @NotNull LiveFragment fragment) {
        super(fragment.requireActivity());
        mActivity = fragment;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_all_channel;
    }

    /**
     * decorFitsSystemWindows(false) 后 XPopup 的 getDecorViewInvisibleHeight 恒非 0,
     * 默认 back 走"隐藏软键盘"分支被吞不关弹窗,这里显式 dismiss
     */
    @Override
    protected boolean onBackPressed() {
        dismiss();
        return true;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mBinding = DialogAllChannelBinding.bind(getPopupImplView());
        initChannelGroupView();
        initLiveChannelView();
    }

    private void initChannelGroupView() {
        mBinding.mGroupGridView.setHasFixedSize(true);
        mBinding.mGroupGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));

        groupAdapter = new LiveChannelGroupNewAdapter();
        groupAdapter.setNewData(mActivity.getUiState().getGroups());
        groupAdapter.setSelectedGroupIndex(mActivity.getUiState().getSelectedGroupIdx());
        mBinding.mGroupGridView.setAdapter(groupAdapter);
        groupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, android.view.View view, int position) {
                mActivity.selectChannelGroup(position, false, -1);
                groupAdapter.setSelectedGroupIndex(position);
                channelAdapter.setNewData(mActivity.getUiState().getChannels());
            }
        });
    }

    private void initLiveChannelView() {
        mBinding.mChannelGridView.setHasFixedSize(true);
        mBinding.mChannelGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));

        channelAdapter = new LiveChannelItemNewAdapter();
        channelAdapter.setNewData(mActivity.getUiState().getChannels());
        channelAdapter.setSelectedChannelIndex(mActivity.getUiState().getSelectedChannelIdx());
        mBinding.mChannelGridView.setAdapter(channelAdapter);
        channelAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, android.view.View view, int position) {
                mActivity.clickLiveChannel(position);
                dismiss();
            }
        });
    }
}
