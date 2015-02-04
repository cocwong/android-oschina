package net.oschina.app.team.fragment;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import net.oschina.app.R;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.base.BaseFragment;
import net.oschina.app.cache.CacheManager;
import net.oschina.app.fragment.MyInformationFragment;
import net.oschina.app.team.adapter.TeamDiaryListAdapter;
import net.oschina.app.team.bean.Team;
import net.oschina.app.team.bean.TeamDiaryList;
import net.oschina.app.team.bean.TeamList;
import net.oschina.app.util.StringUtils;
import net.oschina.app.util.TLog;
import net.oschina.app.util.XmlUtils;

import org.apache.http.Header;
import org.kymjs.kjframe.http.core.KJAsyncTask;
import org.kymjs.kjframe.utils.PreferenceHelper;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;

import com.loopj.android.http.AsyncHttpResponseHandler;

/**
 * 周报模块（外部无关模式,可独立于其他模块）
 * 
 * @author kymjs (https://github.com/kymjs)
 * 
 */
public class TeamDiaryPagerFragment extends BaseFragment {
    private static final String TAG = "TeamDiaryPagerFragment";

    @InjectView(R.id.team_diary_pager)
    ViewPager mPager;
    @InjectView(R.id.team_diary_pager_title)
    TextView mTvTitle;
    @InjectView(R.id.team_diary_pager_calendar)
    ImageView mImgCalendar;
    @InjectView(R.id.team_diary_pager_left)
    ImageView mImgLeft;
    @InjectView(R.id.team_diary_pager_right)
    ImageView mImgRight;

    private Activity aty;
    private Team team;
    private int currentWeek;
    private Map<Integer, TeamDiaryList> dataBundleList; // 用于实现二级缓存

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        aty = getActivity();
        View view = View.inflate(getActivity(),
                R.layout.fragment_team_diarypager, null);
        ButterKnife.inject(this, view);
        initData();
        initView(view);
        return view;
    }

    @Override
    public void initData() {
        super.initData();
        Bundle bundle = getActivity().getIntent().getExtras();
        if (bundle != null) {
            int index = bundle.getInt(MyInformationFragment.TEAM_LIST_KEY, 0);
            String cache = PreferenceHelper.readString(getActivity(),
                    MyInformationFragment.TEAM_LIST_FILE,
                    MyInformationFragment.TEAM_LIST_KEY);
            team = TeamList.toTeamList(cache).get(index);
        } else {
            team = new Team();
            TLog.log(getClass().getSimpleName(), "team对象初始化异常");

        }

        currentWeek = StringUtils.getWeekOfYear();
        dataBundleList = new HashMap<Integer, TeamDiaryList>(currentWeek);
        // 由于数据量比较大，做二级缓存
        KJAsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < currentWeek; i++) {
                    TeamDiaryList dataBundle = ((TeamDiaryList) CacheManager
                            .readObject(aty, TAG + i));
                    if (dataBundle != null) {
                        if (dataBundleList.get(i) != null) {
                            dataBundleList.remove(i);
                        }
                        dataBundleList.put(i, dataBundle);
                    }
                }
            }
        });
    }

    @Override
    public void initView(View view) {
        super.initView(view);
        mPager.setAdapter(new DiaryPagerAdapter());
        mPager.setCurrentItem(currentWeek); // 首先显示当前周，然后左翻页查看历史
        mImgCalendar.setOnClickListener(this);
        mImgLeft.setOnClickListener(this);
        mImgRight.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        switch (v.getId()) {
        case R.id.team_diary_pager_right:
            int currentPage1 = mPager.getCurrentItem();
            if (currentPage1 < currentWeek - 1) {
                mPager.setCurrentItem(currentPage1 + 1);
            }
            break;
        case R.id.team_diary_pager_left:
            int currentPage2 = mPager.getCurrentItem();
            if (currentPage2 > 0) {
                mPager.setCurrentItem(currentPage2 - 1);
            }
            break;
        case R.id.team_diary_pager_calendar:
            break;
        default:
            break;
        }
    }

    /************************* pager adapter *******************************/

    public class DiaryPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return currentWeek;
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View pagerView = View.inflate(aty, R.layout.pager_item_diary, null);
            initPagerContent(pagerView, position + 1); // +1是因为没有第0周，从1开始数
            (container).addView(pagerView);
            return pagerView;
        }

        private void initPagerContent(View pagerView, final int whichWeek) {
            final ListView listview = (ListView) pagerView
                    .findViewById(R.id.diary_listview);
            final SwipeRefreshLayout pullHeadView = (SwipeRefreshLayout) pagerView
                    .findViewById(R.id.swiperefreshlayout);
            initListContent(listview, whichWeek);

            pullHeadView.setOnRefreshListener(new OnRefreshListener() {
                @Override
                public void onRefresh() {
                    if (mState == STATE_REFRESH) {
                        return;
                    } else {
                        // 设置顶部正在刷新
                        setSwipeRefreshLoadingState(pullHeadView);
                        /* !!! 设置耗时操作 !!! */
                        setContentFromNet(pullHeadView, listview, whichWeek);
                    }
                }
            });
            pullHeadView.setColorSchemeResources(R.color.swiperefresh_color1,
                    R.color.swiperefresh_color2, R.color.swiperefresh_color3,
                    R.color.swiperefresh_color4);
        }

        /**
         * 不同的pager显示不同的数据，根据pager动态加载数据
         * 
         * @param view
         * @param position
         */
        private void initListContent(final ListView view, final int whichWeek) {
            TeamDiaryList dataBundle = dataBundleList.get(whichWeek);
            if (dataBundle == null) {
                setContentFromNet(null, view, whichWeek);
            } else {
                setContentFromCache(view, dataBundle);
            }
        }

        /* self annotation */
        private void setContentFromNet(final SwipeRefreshLayout pullHeadView,
                final ListView view, final int whichWeek) {
            OSChinaApi.getDiaryFromWhichWeek(team.getId() + "", "2015",
                    whichWeek + "", new AsyncHttpResponseHandler() {

                        @Override
                        public void onFinish() {
                            super.onFinish();
                            if (pullHeadView != null) {
                                setSwipeRefreshLoadedState(pullHeadView);
                            }
                        }

                        @Override
                        public void onFailure(int arg0, Header[] arg1,
                                byte[] arg2, Throwable arg3) {
                            /* 网络异常 */
                        }

                        @Override
                        public void onSuccess(int arg0, Header[] arg1,
                                byte[] arg2) {
                            final TeamDiaryList bundle = XmlUtils.toBean(
                                    TeamDiaryList.class,
                                    new ByteArrayInputStream(arg2));

                            KJAsyncTask.execute(new Runnable() {
                                // dataBundleList没有加入线程安全，由于whichWeek对应的value只在此处会修改，
                                // 线程冲突概率非常小，为了ListView流畅性，忽略线程安全性
                                @Override
                                public void run() {
                                    if (dataBundleList.get(whichWeek) != null) {
                                        dataBundleList.remove(whichWeek);
                                    }
                                    dataBundleList.put(whichWeek, bundle);
                                    CacheManager.saveObject(aty, bundle, TAG
                                            + whichWeek);
                                }
                            });

                            ListAdapter adapter = view.getAdapter();
                            if (adapter != null
                                    && adapter instanceof TeamDiaryListAdapter) {
                                ((TeamDiaryListAdapter) adapter).refresh(bundle
                                        .getList());
                            } else {
                                view.setAdapter(new TeamDiaryListAdapter(aty,
                                        bundle.getList()));
                            }
                        }
                    });
        }

        /* self annotation */
        private void setContentFromCache(ListView view, TeamDiaryList dataBundle) {
            view.setAdapter(new TeamDiaryListAdapter(aty, dataBundle.getList()));
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
    }
}
