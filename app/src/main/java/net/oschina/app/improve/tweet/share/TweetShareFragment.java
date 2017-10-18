package net.oschina.app.improve.tweet.share;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.widget.NestedScrollView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.oschina.app.R;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.improve.base.fragments.BaseFragment;
import net.oschina.app.improve.bean.Tweet;
import net.oschina.app.improve.bean.simple.About;
import net.oschina.app.improve.bean.simple.Author;
import net.oschina.app.improve.dialog.ShareDialog;
import net.oschina.app.improve.utils.DialogHelper;
import net.oschina.app.improve.utils.parser.TweetParser;
import net.oschina.app.improve.widget.IdentityView;
import net.oschina.app.improve.widget.PortraitView;
import net.oschina.app.improve.widget.SimplexToast;
import net.oschina.app.improve.widget.TweetPicturesLayout;
import net.oschina.app.util.PlatfromUtil;
import net.oschina.app.util.StringUtils;
import net.oschina.common.utils.StreamUtil;

import java.io.File;
import java.io.FileOutputStream;

import butterknife.Bind;

/**
 * 分享动弹界面
 * Created by huanghaibin on 2017/10/16.
 */

public class TweetShareFragment extends BaseFragment implements Runnable{

    @Bind(R.id.identityView)
    IdentityView mIdentityView;
    @Bind(R.id.iv_portrait)
    PortraitView ivPortrait;
    @Bind(R.id.tv_nick)
    TextView tvNick;
    @Bind(R.id.tv_time)
    TextView tvTime;
    @Bind(R.id.tv_client)
    TextView tvClient;
    @Bind(R.id.iv_thumbup)
    ImageView ivThumbup;
    @Bind(R.id.tweet_tv_record)
    TextView mContent;
    @Bind(R.id.tweet_pics_layout)
    TweetPicturesLayout mLayoutGrid;

    @Bind(R.id.tv_ref_title)
    TextView mViewRefTitle;
    @Bind(R.id.tv_ref_content)
    TextView mViewRefContent;
    @Bind(R.id.layout_ref_images)
    TweetPicturesLayout mLayoutRefImages;
    @Bind(R.id.layout_ref)
    LinearLayout mLayoutRef;

    @Bind(R.id.nsv_content)
    NestedScrollView mViewScroller;
    private ShareDialog mShareDialog;
    private Bitmap mBitmap;
    private ProgressDialog mDialog;


    public static TweetShareFragment newInstance() {
        return new TweetShareFragment();
    }

    @Override
    protected void initWidget(View root) {
        super.initWidget(root);
        mDialog = DialogHelper.getProgressDialog(mContext);
        mDialog.setMessage("请稍候...");
        mShareDialog = new ShareDialog(getActivity(), -1, false);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_tweet_share;
    }

    public void init(Tweet tweet) {
        if (mContext == null)
            return;
        Author author = tweet.getAuthor();
        mIdentityView.setup(author);
        if (author != null) {
            ivPortrait.setup(author);
            tvNick.setText(author.getName());
        } else {
            ivPortrait.setup(0, "匿名用户", "");
            tvNick.setText("匿名用户");
        }
        if (!TextUtils.isEmpty(tweet.getPubDate()))
            tvTime.setText(StringUtils.formatSomeAgo(tweet.getPubDate()));
        PlatfromUtil.setPlatFromString(tvClient, tweet.getAppClient());
        if (tweet.isLiked()) {
            ivThumbup.setSelected(true);
        } else {
            ivThumbup.setSelected(false);
        }
        if (!TextUtils.isEmpty(tweet.getContent())) {
            String content = tweet.getContent().replaceAll("[\n\\s]+", " ");
            mContent.setText(TweetParser.getInstance().parse(mContext, content));
            mContent.setMovementMethod(LinkMovementMethod.getInstance());
        }

        mLayoutGrid.setImage(tweet.getImages());

        /* -- about reference -- */
        if (tweet.getAbout() != null) {
            mLayoutRef.setVisibility(View.VISIBLE);
            About about = tweet.getAbout();
            mLayoutRefImages.setImage(about.getImages());

            if (!About.check(about)) {
                mViewRefTitle.setVisibility(View.VISIBLE);
                mViewRefTitle.setText("不存在或已删除的内容");
                mViewRefContent.setText("抱歉，该内容不存在或已被删除");
            } else {
                if (about.getType() == OSChinaApi.COMMENT_TWEET) {
                    mViewRefTitle.setVisibility(View.GONE);
                    String aName = "@" + about.getTitle();
                    String cnt = about.getContent();
                    Spannable spannable = TweetParser.getInstance().parse(mContext, cnt);
                    SpannableStringBuilder builder = new SpannableStringBuilder();
                    builder.append(aName).append(": ");
                    builder.append(spannable);
                    ForegroundColorSpan span = new ForegroundColorSpan(
                            getResources().getColor(R.color.day_colorPrimary));
                    builder.setSpan(span, 0, aName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    mViewRefContent.setText(builder);
                } else {
                    mViewRefTitle.setVisibility(View.VISIBLE);
                    mViewRefTitle.setText(about.getTitle());
                    mViewRefContent.setText(about.getContent());
                }
            }
        } else {
            mLayoutRef.setVisibility(View.GONE);
        }
    }

    @Override
    public void run() {
        mBitmap = create(mViewScroller.getChildAt(0));
        mShareDialog.bitmap(mBitmap);
        mDialog.dismiss();
        mShareDialog.show();
    }

    public void share() {
        recycle();
        mDialog.show();
        mRoot.postDelayed(this, 2000);
        mBitmap = create(mViewScroller.getChildAt(0));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void save() {
        recycle();
        mBitmap = create(mViewScroller.getChildAt(0));
        FileOutputStream os = null;
        try {
            String url = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .getAbsolutePath() + File.separator + "开源中国/save/";
            File file = new File(url);
            if (!file.exists()) {
                file.mkdirs();
            }
            String path = String.format("%s%s.jpg", url, System.currentTimeMillis());
            os = new FileOutputStream(path);
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();
            SimplexToast.show(mContext, "保存成功");
            Uri localUri = Uri.fromFile(new File(path));
            Intent localIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, localUri);
            getActivity().sendBroadcast(localIntent);
        } catch (Exception e) {
            e.printStackTrace();
            SimplexToast.show(mContext, "保存失败");
        } finally {
            StreamUtil.close(os);
            recycle();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mShareDialog.dismiss();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recycle();
    }

    private static Bitmap create(View v) {
        try {
            int w = v.getWidth();
            int h = v.getHeight();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);
            v.layout(0, 0, w, h);
            v.draw(c);
            return bmp;
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
            return null;
        }
    }

    private void recycle() {
        if (mBitmap != null && mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
    }
}