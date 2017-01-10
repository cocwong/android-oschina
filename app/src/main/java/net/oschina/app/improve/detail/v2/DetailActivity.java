package net.oschina.app.improve.detail.v2;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.oschina.app.R;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.improve.account.AccountHelper;
import net.oschina.app.improve.account.activity.LoginActivity;
import net.oschina.app.improve.base.activities.BaseBackActivity;
import net.oschina.app.improve.bean.SubBean;
import net.oschina.app.improve.bean.comment.Comment;
import net.oschina.app.improve.bean.simple.About;
import net.oschina.app.improve.behavior.CommentBar;
import net.oschina.app.improve.comment.CommentsActivity;
import net.oschina.app.improve.comment.OnCommentClickListener;
import net.oschina.app.improve.dialog.ShareDialog;
import net.oschina.app.improve.tweet.service.TweetPublishService;
import net.oschina.app.improve.user.activities.UserSelectFriendsActivity;
import net.oschina.app.improve.utils.DialogHelper;
import net.oschina.app.ui.empty.EmptyLayout;
import net.oschina.app.util.HTMLUtil;
import net.oschina.app.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新版本详情页实现
 * Created by haibin
 * on 2016/11/30.
 */

public abstract class DetailActivity extends BaseBackActivity implements
        DetailContract.EmptyView, Runnable,
        OnCommentClickListener {

    protected ProgressDialog mProgressDialog;
    protected DetailPresenter mPresenter;
    protected EmptyLayout mEmptyLayout;
    protected DetailFragment mDetailFragment;
    protected ShareDialog mAlertDialog;
    protected TextView mCommentCountView;

    protected CommentBar mDelegation;

    protected SubBean mBean;

    protected long mCommentId;
    protected long mCommentAuthorId;
    protected boolean mInputDoubleEmpty = false;

    @Override
    protected int getContentView() {
        return R.layout.activity_detail_v2;
    }

    @Override
    protected void initWidget() {
        super.initWidget();

        LinearLayout layComment = (LinearLayout) findViewById(R.id.ll_comment);
        mEmptyLayout = (EmptyLayout) findViewById(R.id.lay_error);
        mEmptyLayout.setOnLayoutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEmptyLayout.getErrorState() != EmptyLayout.NETWORK_LOADING) {
                    mEmptyLayout.setErrorType(EmptyLayout.NETWORK_LOADING);
                    mPresenter.getDetail();
                }
            }
        });
        mBean = (SubBean) getIntent().getSerializableExtra("sub_bean");
        mDetailFragment = getDetailFragment();
        addFragment(R.id.lay_container, mDetailFragment);
        mPresenter = new DetailPresenter(mDetailFragment, this, mBean);
        if (!mPresenter.isHideCommentBar()) {
            mDelegation = CommentBar.delegation(this, layComment);
            mDelegation.setFavDrawable(mBean.isFavorite() ? R.drawable.ic_faved : R.drawable.ic_fav);

            mDelegation.setFavListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!AccountHelper.isLogin()) {
                        LoginActivity.show(DetailActivity.this);
                        return;
                    }
                    mPresenter.favReverse();
                }
            });

            mDelegation.getBottomSheet().setMentionListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ((AccountHelper.isLogin())) {
                        UserSelectFriendsActivity.show(DetailActivity.this);
                    } else {
                        LoginActivity.show(DetailActivity.this, 1);
                    }
                }
            });

            mDelegation.setShareListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toShare(mBean.getTitle(), mBean.getBody(), mBean.getHref());
                }
            });

            mDelegation.getBottomSheet().getEditText().setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL) {
                        handleKeyDel();
                    }
                    return false;
                }
            });
            mDelegation.getBottomSheet().setCommitListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDialog("正在提交评论...");
                    mPresenter.addComment(mBean.getId(),
                            mBean.getType(),
                            mDelegation.getBottomSheet().getCommentText(),
                            0,
                            mCommentId,
                            mCommentAuthorId);
                }
            });
        }
        mEmptyLayout.post(new Runnable() {
            @Override
            public void run() {
                mPresenter.getCache();
                mPresenter.getDetail();
            }
        });
    }

    @Override
    public void hideEmptyLayout() {
        mEmptyLayout.setErrorType(EmptyLayout.HIDE_LAYOUT);
        if (mCommentCountView != null) {
            mCommentCountView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CommentsActivity.show(DetailActivity.this, mBean.getId(), mBean.getType(), OSChinaApi.COMMENT_NEW_ORDER);
                }
            });
        }
    }

    @Override
    public void showErrorLayout(int errorType) {
        mEmptyLayout.setErrorType(errorType);
    }

    @Override
    public void showGetDetailSuccess(SubBean bean) {
        this.mBean = bean;
        if (mDelegation != null)
            mDelegation.setFavDrawable(mBean.isFavorite() ? R.drawable.ic_faved : R.drawable.ic_fav);
        if (mCommentCountView != null && mBean.getStatistics() != null) {
            mCommentCountView.setText(String.valueOf(mBean.getStatistics().getComment()));
        }
    }

    @Override
    public void run() {
        hideEmptyLayout();
        mDetailFragment.onPageFinished();
    }


    @Override
    public void showFavReverseSuccess(boolean isFav, int strId) {
        if (mDelegation != null) {
            mDelegation.setFavDrawable(isFav ? R.drawable.ic_faved : R.drawable.ic_fav);
        }
    }

    @Override
    public void showCommentSuccess(Comment comment) {
        hideDialog();
        if (mDelegation == null)
            return;
        if (mDelegation.getBottomSheet().isSyncToTweet()) {
            TweetPublishService.startActionPublish(this,
                    mDelegation.getBottomSheet().getCommentText(), null,
                    About.buildShare(mBean.getId(), mBean.getType()));
        }
        Toast.makeText(this, getString(R.string.pub_comment_success), Toast.LENGTH_SHORT).show();
        mDelegation.getCommentText().setHint(getString(R.string.add_comment_hint));
        mDelegation.getBottomSheet().getEditText().setText("");
        mDelegation.getBottomSheet().getEditText().setHint(getString(R.string.add_comment_hint));
        mDelegation.getBottomSheet().dismiss();

    }

    @Override
    public void showCommentError(String message) {
        hideDialog();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_detail, menu);
        MenuItem item = menu.findItem(R.id.menu_scroll_comment);
        if (item != null) {
            View action = item.getActionView();
            if (action != null) {
                View tv = action.findViewById(R.id.tv_comment_count);
                if (tv != null) {
                    mCommentCountView = (TextView) tv;
                    if (mBean.getStatistics() != null)
                        mCommentCountView.setText(mBean.getStatistics().getComment() + "");
                }
            }
        }
        return true;
    }


    @SuppressWarnings("LoopStatementThatDoesntLoop")
    protected boolean toShare(String title, String content, String url) {
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content) || TextUtils.isEmpty(url))
            return false;

        String imageUrl = null;

        //1.如果是活动直接在分享中加入活动icon
        if (mBean != null && mBean.getType() == OSChinaApi.CATALOG_EVENT) {
            List<SubBean.Image> images = mBean.getImages();
            if (images != null && images.size() > 0) {
                imageUrl = images.get(0).getThumb();
            }
        } else {
            //2.不是活动类型,匹配内容中是否有图片，有就返回第一张图片的url
            //"<\\s*img\\s+([^>]*)\\s*/>"
            String regex = "<img src=\"([^\"]+)\"";

            Pattern pattern = Pattern.compile(regex);

            Matcher matcher = pattern.matcher(content);

            while (matcher.find()) {
                imageUrl = matcher.group(1);
                break;
            }
        }

        content = content.trim();
        if (content.length() > 55) {
            content = HTMLUtil.delHTMLTag(content);
            if (content.length() > 55)
                content = StringUtils.getSubString(0, 55, content);
        } else {
            content = HTMLUtil.delHTMLTag(content);
        }
        if (TextUtils.isEmpty(content))
            return false;

        // 分享
        if (mAlertDialog == null) {
            mAlertDialog = new
                    ShareDialog(this, mBean.getId())
                    .type(mBean.getType())
                    .title(title)
                    .content(content)
                    .imageUrl(imageUrl)//如果没有图片，即url为null，直接加入app默认分享icon
                    .url(url).with();
        }
        mAlertDialog.show();

        return true;
    }

    @Override
    public void onClick(View view, Comment comment) {
        mCommentId = comment.getId();
        mCommentAuthorId = comment.getAuthor().getId();
        mDelegation.getCommentText().setHint(String.format("%s %s", getResources().getString(R.string.reply_hint), comment.getAuthor().getName()));
        mDelegation.getBottomSheet().show(String.format("%s %s", getResources().getString(R.string.reply_hint), comment.getAuthor().getName()));
    }

    protected void handleKeyDel() {
        if (mCommentId != mBean.getId()) {
            if (TextUtils.isEmpty(mDelegation.getBottomSheet().getCommentText())) {
                if (mInputDoubleEmpty) {
                    mCommentId = mBean.getId();
                    mCommentAuthorId = 0;
                    mDelegation.setCommentHint(getString(R.string.pub_comment_hint));
                    mDelegation.getBottomSheet().getEditText().setHint(getString(R.string.pub_comment_hint));
                } else {
                    mInputDoubleEmpty = true;
                }
            } else {
                mInputDoubleEmpty = false;
            }
        }
    }

    protected String getExtraString(Object object) {
        return object == null ? "" : object.toString();
    }

    protected int getExtraInt(Object object) {
        return object == null ? 0 : Double.valueOf(object.toString()).intValue();
    }

    protected void showDialog(String message) {
        if (mProgressDialog == null)
            mProgressDialog = DialogHelper.getProgressDialog(this);
        mProgressDialog.setMessage(message);
        mProgressDialog.show();
    }

    protected void hideDialog() {
        if (mProgressDialog == null)
            return;
        mProgressDialog.dismiss();
    }

    protected abstract DetailFragment getDetailFragment();

    @Override
    public void finish() {
        DetailCache.addCache(mBean);
        super.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
            mDelegation.getBottomSheet().handleSelectFriendsResult(data);
            mDelegation.setCommentHint(mDelegation.getBottomSheet().getEditText().getHint().toString());
        }
    }
}