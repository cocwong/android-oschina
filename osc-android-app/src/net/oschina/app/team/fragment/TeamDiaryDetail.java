package net.oschina.app.team.fragment;

import java.util.List;

import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.base.BaseFragment;
import net.oschina.app.emoji.EmojiFragment;
import net.oschina.app.emoji.EmojiFragment.EmojiTextListener;
import net.oschina.app.interf.EmojiFragmentControl;
import net.oschina.app.team.adapter.DiaryDetailAdapter;
import net.oschina.app.team.bean.TeamDiary;
import net.oschina.app.team.bean.TeamDiaryDetailBean;
import net.oschina.app.team.bean.TeamRepliesList;
import net.oschina.app.team.bean.TeamReply;
import net.oschina.app.team.viewpagefragment.TeamDiaryFragment;
import net.oschina.app.ui.empty.EmptyLayout;
import net.oschina.app.util.StringUtils;
import net.oschina.app.util.TDevice;
import net.oschina.app.util.UIHelper;
import net.oschina.app.util.XmlUtils;
import net.oschina.app.widget.AvatarView;

import org.apache.http.Header;
import org.kymjs.kjframe.utils.KJLoger;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;

import com.loopj.android.http.AsyncHttpResponseHandler;

/**
 * 周报详情<br>
 * 逻辑介绍：用Listview来显示评论内容，在ListView的HeadView中添加本周报的详细内容与周报列表的item。
 * 周报的详细内容通过动态添加addView的方式
 * 
 * @author kymjs (https://github.com/kymjs)
 */
public class TeamDiaryDetail extends BaseFragment implements EmojiTextListener,
        EmojiFragmentControl {

    private static final String CACHE_KEY_PREFIX = "team_diary_detail_";

    @InjectView(R.id.listview)
    ListView mList;
    @InjectView(R.id.swiperefreshlayout)
    SwipeRefreshLayout mSwiperefreshlayout;
    @InjectView(R.id.error_layout)
    EmptyLayout mErrorLayout;

    private TeamDiary diaryData;
    private int teamid;
    private Activity aty;
    private DiaryDetailAdapter adapter;

    protected EmojiFragment mEmojiFragment;

    private LinearLayout footerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = View.inflate(getActivity(),
                R.layout.fragment_pull_refresh_listview, null);
        aty = getActivity();
        ButterKnife.inject(this, rootView);
        initData();
        initView(rootView);
        return rootView;
    }

    @Override
    public void initData() {
        super.initData();
        Bundle bundle = aty.getIntent().getBundleExtra("diary");
        if (bundle != null) {
            teamid = bundle.getInt(TeamDiaryFragment.TEAMID_KEY);
            diaryData = (TeamDiary) bundle
                    .getSerializable(TeamDiaryFragment.DIARYDETAIL_KEY);
        } else {
            diaryData = new TeamDiary();
            Log.e("debug", getClass().getSimpleName() + "diaryData初始化异常");
        }
        KJLoger.debug("TeamDiaryDetail=81===id=：" + diaryData.getId());
    }

    @Override
    public void initView(View view) {
        super.initView(view);
        mList.setDivider(null);
        mList.setSelector(android.R.color.transparent);
        mList.addHeaderView(initHeaderView());
        mList.addFooterView(initFooterView());

        mSwiperefreshlayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mState == STATE_REFRESH) {
                    return;
                } else {
                    // 设置顶部正在刷新
                    setSwipeRefreshLoadingState(mSwiperefreshlayout);
                    /* !!! 设置耗时操作 !!! */
                    initCommitLayout();
                }
            }
        });
        mSwiperefreshlayout.setColorSchemeResources(
                R.color.swiperefresh_color1, R.color.swiperefresh_color2,
                R.color.swiperefresh_color3, R.color.swiperefresh_color4);

        initListData();
        initCommitLayout();
    }

    /**
     * 设置顶部正在加载的状态
     */
    private void setSwipeRefreshLoadingState(
            SwipeRefreshLayout mSwipeRefreshLayout) {
        mState = STATE_REFRESH;
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.setRefreshing(true);
            // 防止多次重复刷新
            mSwipeRefreshLayout.setEnabled(false);
        }
    }

    /**
     * 设置顶部加载完毕的状态
     */
    private void setSwipeRefreshLoadedState(
            SwipeRefreshLayout mSwipeRefreshLayout) {
        mState = STATE_NOMORE;
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.setRefreshing(false);
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    /**
     * 初始化头部周报Title
     * 
     * @return
     */
    private View initHeaderView() {
        View headerView = inflateView(R.layout.item_team_diarydetail_head);
        AvatarView headImg = (AvatarView) headerView
                .findViewById(R.id.event_listitem_userface);
        TextView userName = (TextView) headerView
                .findViewById(R.id.event_listitem_username);
        WebView content = (WebView) headerView
                .findViewById(R.id.team_diary_webview);
        TextView time = (TextView) headerView
                .findViewById(R.id.event_listitem_date);
        headImg.setAvatarUrl(diaryData.getAuthor().getPortrait());
        userName.setText(diaryData.getAuthor().getName());

        UIHelper.initWebView(content);
        fillWebViewBody(content);
        // content.setText(Html.fromHtml(diaryData.getTitle()));
        time.setText(StringUtils.friendly_time(diaryData.getCreateTime()));
        return headerView;
    }

    /**
     * 填充webview内容
     */
    private void fillWebViewBody(WebView mContent) {
        StringBuffer body = new StringBuffer();
        body.append(UIHelper.WEB_STYLE + UIHelper.WEB_LOAD_IMAGES);
        body.append(diaryData.getTitle());
        UIHelper.addWebImageShow(getActivity(), mContent);
        mContent.loadDataWithBaseURL(null, body.toString(), "text/html",
                "utf-8", null);
    }

    private View initFooterView() {
        footerView = new LinearLayout(aty);
        footerView.setPadding(20, 0, 20, 20);
        footerView.setOrientation(LinearLayout.VERTICAL);
        View line = View.inflate(aty, R.layout.list_head_commnt_line, null);
        footerView.addView(line);
        return footerView;
    }

    private void initListData() {
        OSChinaApi.getDiaryDetail(teamid, diaryData.getId(),
                new AsyncHttpResponseHandler() {
                    @Override
                    public void onStart() {
                        super.onStart();
                        mErrorLayout.setErrorType(EmptyLayout.NETWORK_LOADING);
                    }

                    @Override
                    public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
                        TeamDiaryDetailBean data = XmlUtils.toBean(
                                TeamDiaryDetailBean.class, arg2);
                        adapter = new DiaryDetailAdapter(aty, data
                                .getTeamDiary().getDetail());
                        mList.setAdapter(adapter);
                        mErrorLayout.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(int arg0, Header[] arg1, byte[] arg2,
                            Throwable arg3) {
                        mErrorLayout.setErrorType(EmptyLayout.NETWORK_ERROR);
                        mErrorLayout.setErrorMessage("网络不好，请稍后重试");
                    }

                });
    }

    private void initCommitLayout() {
        OSChinaApi.getDiaryComment(teamid, diaryData.getId(),
                new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int arg0, Header[] arg1,
                            final byte[] arg2) {
                        List<TeamReply> datas = XmlUtils.toBean(
                                TeamRepliesList.class, arg2).getList();
                        footerView.removeAllViews();
                        View line = View.inflate(aty,
                                R.layout.list_head_commnt_line, null);
                        footerView.addView(line);
                        if (datas.size() > 0) {
                            TextView commentCount = (TextView) line
                                    .findViewById(R.id.tv_comment_count);
                            commentCount.setText("评论(" + datas.size() + ")");
                        }
                        for (final TeamReply data : datas) {
                            View layout = View.inflate(aty,
                                    R.layout.team_list_cell_comment, null);
                            AvatarView head = (AvatarView) layout
                                    .findViewById(R.id.iv_avatar);
                            head.setAvatarUrl(data.getAuthor().getPortrait());
                            final TextView name = (TextView) layout
                                    .findViewById(R.id.tv_name);
                            name.setText(data.getAuthor().getName());
                            TextView time = (TextView) layout
                                    .findViewById(R.id.tv_time);
                            time.setText(StringUtils.friendly_time(data
                                    .getCreateTime()));
                            TextView content = (TextView) layout
                                    .findViewById(R.id.tv_content);
                            content.setText(stripTags(data.getContent()));
                            TextView from = (TextView) layout
                                    .findViewById(R.id.tv_from);
                            if (StringUtils.isEmpty(data.getAppName())) {
                                from.setVisibility(View.GONE);
                            } else {
                                from.setVisibility(View.VISIBLE);
                                from.setText(data.getAppName());
                            }
                            layout.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mEmojiFragment.setTag(v);
                                    mEmojiFragment.setInputHint("回复"
                                            + data.getAuthor().getName() + ":");
                                    mEmojiFragment.requestFocusInput();
                                }
                            });

                            footerView.addView(layout);
                        }
                        footerView.invalidate();
                        if (adapter != null) {
                            adapter.notifyDataSetInvalidated();
                        }
                    }

                    @Override
                    public void onFailure(int arg0, Header[] arg1, byte[] arg2,
                            Throwable arg3) {}

                    @Override
                    public void onFinish() {
                        super.onFinish();
                        setSwipeRefreshLoadedState(mSwiperefreshlayout);
                    }
                });
    }

    /**
     * 移除字符串中的Html标签
     * 
     * @author kymjs (https://github.com/kymjs)
     * @param pHTMLString
     * @return
     */
    public static Spanned stripTags(final String pHTMLString) {
        String str = pHTMLString.replaceAll("<\\s*>", "");
        str = str.replaceAll("<\\s*img\\s+([^>]*)\\s*>", "").trim();
        return Html.fromHtml(str);
    }

    @Override
    public void setEmojiFragment(EmojiFragment fragment) {
        mEmojiFragment = fragment;
        mEmojiFragment.setEmojiTextListener(this);
    }

    @Override
    public void onSendClick(String text) {
        if (!TDevice.hasInternet()) {
            AppContext.showToastShort(R.string.tip_network_error);
            return;
        }
        if (!AppContext.getInstance().isLogin()) {
            UIHelper.showLoginActivity(getActivity());
            mEmojiFragment.hideKeyboard();
            return;
        }
        if (TextUtils.isEmpty(text)) {
            AppContext.showToastShort(R.string.tip_comment_content_empty);
            mEmojiFragment.requestFocusInput();
            return;
        }
        OSChinaApi.sendComment(AppContext.getInstance().getLoginUid(), teamid,
                diaryData.getId(), text, new AsyncHttpResponseHandler() {

                    @Override
                    public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
                        initCommitLayout();
                        mEmojiFragment.reset();
                    }

                    @Override
                    public void onFailure(int arg0, Header[] arg1, byte[] arg2,
                            Throwable arg3) {}
                });
    }
}