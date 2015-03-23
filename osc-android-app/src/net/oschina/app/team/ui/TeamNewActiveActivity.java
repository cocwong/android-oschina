package net.oschina.app.team.ui;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.kymjs.kjframe.KJBitmap;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.viewpagerindicator.CirclePageIndicator;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.api.remote.OSChinaApi;
import net.oschina.app.api.remote.OSChinaTeamApi;
import net.oschina.app.base.BaseActivity;
import net.oschina.app.bean.Result;
import net.oschina.app.bean.ResultBean;
import net.oschina.app.emoji.Emoji;
import net.oschina.app.emoji.EmojiEditText;
import net.oschina.app.emoji.EmojiHelper;
import net.oschina.app.emoji.EmojiViewPagerAdapter;
import net.oschina.app.emoji.SoftKeyboardStateHelper;
import net.oschina.app.emoji.EmojiViewPagerAdapter.OnClickEmojiListener;
import net.oschina.app.emoji.SoftKeyboardStateHelper.SoftKeyboardStateListener;
import net.oschina.app.team.bean.Team;
import net.oschina.app.team.bean.TeamMember;
import net.oschina.app.team.bean.TeamMemberList;
import net.oschina.app.ui.dialog.CommonDialog;
import net.oschina.app.ui.dialog.DialogHelper;
import net.oschina.app.util.FileUtil;
import net.oschina.app.util.ImageUtils;
import net.oschina.app.util.StringUtils;
import net.oschina.app.util.TDevice;
import net.oschina.app.util.XmlUtils;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 团队新动态 TeamNewActiveFragment.java
 * 
 * @author 火蚁(http://my.oschina.net/u/253900)
 * 
 * @data 2015-3-6 下午6:39:53
 */
public class TeamNewActiveActivity extends BaseActivity implements
	SoftKeyboardStateListener, OnClickEmojiListener {

    private static final int MAX_TEXT_LENGTH = 160;
    private static final String TEXT_SOFTWARE = "#请输入软件名#";

    private Team mTeam;

    private MenuItem mMenuSend;

    @InjectView(R.id.view_pager)
    ViewPager mViewPager;

    @InjectView(R.id.indicator)
    CirclePageIndicator mIndicator;

    @InjectView(R.id.ib_emoji_keyboard)
    ImageButton mIbEmoji;

    @InjectView(R.id.ib_picture)
    ImageButton mIbPicture;

    @InjectView(R.id.ib_mention)
    ImageButton mIbMention;

    @InjectView(R.id.ib_trend_software)
    ImageButton mIbTrendSoftware;

    @InjectView(R.id.tv_clear)
    TextView mTvClear;

    @InjectView(R.id.ly_emoji)
    View mLyEmoji;

    @InjectView(R.id.rl_img)
    View mLyImage;

    @InjectView(R.id.iv_img)
    ImageView mIvImage;

    @InjectView(R.id.et_content)
    EmojiEditText mEtInput;

    private EmojiViewPagerAdapter mPagerAdapter;
    private SoftKeyboardStateHelper mKeyboardHelper;
    private boolean mIsKeyboardVisible;
    private boolean mNeedHideEmoji;
    private int mCurrentKeyboardHeigh;

    private String theLarge, theThumbnail;
    private File imgFile;

    private final KJBitmap kjb = KJBitmap.create();

    private final Handler handler = new Handler() {
	@Override
	public void handleMessage(Message msg) {
	    if (msg.what == 1 && msg.obj != null) {
		// 显示图片
		mIvImage.setImageBitmap((Bitmap) msg.obj);
		mLyImage.setVisibility(View.VISIBLE);
	    }
	}
    };

    @Override
    protected int getLayoutId() {
	// TODO Auto-generated method stub
	return R.layout.activity_team_active_pub;
    }

    @Override
    protected boolean hasBackButton() {
	// TODO Auto-generated method stub
	return true;
    }

    @Override
    @OnClick({ R.id.ib_picture, R.id.ib_mention, R.id.ib_trend_software,
	    R.id.ib_emoji_keyboard, R.id.iv_clear_img, R.id.tv_clear })
    public void onClick(View v) {
	// TODO Auto-generated method stub
	switch (v.getId()) {
	case R.id.ib_picture:
	    handleSelectPicture();
	    break;
	case R.id.ib_mention:
	    tryToShowMetionUser();
	    break;
	case R.id.ib_trend_software:
	    insertTrendSoftware();
	    break;

	case R.id.ib_emoji_keyboard:
	    if (mLyEmoji.getVisibility() == View.GONE) {
		mNeedHideEmoji = true;
		tryShowEmojiPanel();
	    } else {
		tryHideEmojiPanel();
	    }
	    break;
	case R.id.iv_clear_img:
	    mIvImage.setImageBitmap(null);
	    mLyImage.setVisibility(View.GONE);
	    imgFile = null;
	    break;
	case R.id.tv_clear:
	    handleClearWords();
	    break;
	default:
	    break;
	}

    }

    private void handleClearWords() {
	if (TextUtils.isEmpty(mEtInput.getText().toString()))
	    return;
	final CommonDialog dialog = DialogHelper
		.getPinterestDialogCancelable(this);
	dialog.setMessage(R.string.clearwords);
	dialog.setPositiveButton(R.string.ok,
		new DialogInterface.OnClickListener() {

		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
			mEtInput.getText().clear();
			if (mIsKeyboardVisible) {
			    TDevice.showSoftKeyboard(mEtInput);
			}
		    }
		});
	dialog.setNegativeButton(R.string.cancle, null);
	dialog.show();
    }

    private void insertTrendSoftware() {
	// 在光标所在处插入“#软件名#”
	int curTextLength = mEtInput.getText().length();
	if (curTextLength >= MAX_TEXT_LENGTH)
	    return;
	String software = TEXT_SOFTWARE;
	int start, end;
	if ((MAX_TEXT_LENGTH - curTextLength) >= software.length()) {
	    start = mEtInput.getSelectionStart() + 1;
	    end = start + software.length() - 2;
	} else {
	    int num = MAX_TEXT_LENGTH - curTextLength;
	    if (num < software.length()) {
		software = software.substring(0, num);
	    }
	    start = mEtInput.getSelectionStart() + 1;
	    end = start + software.length() - 1;
	}
	if (start > MAX_TEXT_LENGTH || end > MAX_TEXT_LENGTH) {
	    start = MAX_TEXT_LENGTH;
	    end = MAX_TEXT_LENGTH;
	}
	mEtInput.getText().insert(mEtInput.getSelectionStart(), software);
	mEtInput.setSelection(start, end);// 设置选中文字
    }

    @Override
    public void initView() {
	// TODO Auto-generated method stub
	ButterKnife.inject(this);
	setActionBarTitle(R.string.team_new_active);
	mTvClear.setText(String.valueOf(MAX_TEXT_LENGTH));
	mEtInput.addTextChangedListener(new TextWatcher() {

	    @Override
	    public void onTextChanged(CharSequence s, int start, int before,
		    int count) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void beforeTextChanged(CharSequence s, int start, int count,
		    int after) {
		// TODO Auto-generated method stub

	    }

	    @Override
	    public void afterTextChanged(Editable s) {
		// TODO Auto-generated method stub
		updateSendMenu();
		mTvClear.setText((MAX_TEXT_LENGTH - s.length()) + "");
	    }
	});

	initEmoji();

	mKeyboardHelper = new SoftKeyboardStateHelper(
		this.findViewById(R.id.container));
	mKeyboardHelper.addSoftKeyboardStateListener(this);
    }

    private void initEmoji() {
	Map<String, Emoji> emojis = EmojiHelper.qq_emojis_nos;
	// int pagerSize = emojis.size() / 20;
	EmojiHelper.initEmojis();

	List<Emoji> allEmojis = new ArrayList<Emoji>();
	Iterator<String> itr1 = emojis.keySet().iterator();
	while (itr1.hasNext()) {
	    Emoji ej = emojis.get(itr1.next());
	    allEmojis.add(new Emoji(ej.getResId(), ej.getValue(), ej
		    .getValueNo(), ej.getIndex()));
	}
	Collections.sort(allEmojis);

	List<List<Emoji>> pagers = new ArrayList<List<Emoji>>();
	List<Emoji> es = null;
	int size = 0;
	boolean justAdd = false;
	for (Emoji ej : allEmojis) {
	    if (size == 0) {
		es = new ArrayList<Emoji>();
	    }
	    es.add(new Emoji(ej.getResId(), ej.getValue(), ej.getValueNo()));
	    size++;
	    if (size == 20) {
		pagers.add(es);
		size = 0;
		justAdd = true;
	    } else {
		justAdd = false;
	    }
	}
	if (!justAdd && es != null) {
	    pagers.add(es);
	}

	int emojiHeight = caculateEmojiPanelHeight();

	mPagerAdapter = new EmojiViewPagerAdapter(this, pagers, emojiHeight,
		this);
	mViewPager.setAdapter(mPagerAdapter);
	mIndicator.setViewPager(mViewPager);

	int mode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
		| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
	getWindow().setSoftInputMode(mode);
    }

    private int caculateEmojiPanelHeight() {
	if (mCurrentKeyboardHeigh == 0) {
	    mCurrentKeyboardHeigh = (int) TDevice.dpToPixel(180);
	}
	int emojiPanelHeight = (int) (mCurrentKeyboardHeigh - TDevice
		.dpToPixel(20));
	int emojiHeight = emojiPanelHeight / 3;

	LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
		LinearLayout.LayoutParams.MATCH_PARENT, emojiPanelHeight);
	mViewPager.setLayoutParams(lp);
	if (mPagerAdapter != null) {
	    mPagerAdapter.setEmojiHeight(emojiHeight);
	}
	return emojiHeight;
    }
    
    private void updateSendMenu() {
	if (mEtInput.getText().length() == 0) {
	    mMenuSend.setEnabled(false);
	    mMenuSend.setIcon(R.drawable.actionbar_unsend_icon);
	} else {
	    mMenuSend.setEnabled(true);
	    mMenuSend.setIcon(R.drawable.actionbar_send_icon);
	}
    }

    @Override
    public void initData() {
	// TODO Auto-generated method stub
	mTeam = (Team) getIntent().getExtras().getSerializable(
		TeamMainActivity.BUNDLE_KEY_TEAM);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	// TODO Auto-generated method stub
	getMenuInflater().inflate(R.menu.pub_new_team_active_menu, menu);
	mMenuSend = menu.findItem(R.id.public_menu_send);
	updateSendMenu();
	return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	// TODO Auto-generated method stub
	switch (item.getItemId()) {
	case R.id.public_menu_send:
	    handleSubmit();
	    break;

	default:
	    break;
	}
	return super.onOptionsItemSelected(item);
    }

    private void handleSubmit() {
	String content = mEtInput.getText().toString();
	if (StringUtils.isEmpty(content) || mTeam == null)
	    return;
	OSChinaTeamApi.pubTeamNewActive(mTeam.getId(), content, imgFile,
		new AsyncHttpResponseHandler() {

		    @Override
		    public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
			// TODO Auto-generated method stub
			Result result = XmlUtils.toBean(ResultBean.class, arg2)
				.getResult();
			if (result != null && result.OK()) {
			    AppContext.showToast(result.getErrorMessage());
			    finish();
			} else {
			    AppContext.showToast(result.getErrorMessage());
			}
		    }

		    @Override
		    public void onFailure(int arg0, Header[] arg1, byte[] arg2,
			    Throwable arg3) {
			// TODO Auto-generated method stub
			AppContext.showToast("发表失败，请检查下你的网络");
		    }

		    @Override
		    public void onStart() {
			// TODO Auto-generated method stub
			super.onStart();
			showWaitDialog("提交中...");
		    }

		    @Override
		    public void onFinish() {
			// TODO Auto-generated method stub
			super.onFinish();
			hideWaitDialog();
		    }
		});
    }

    @Override
    public void onEmojiClick(Emoji emoji) {
	mEtInput.insertEmoji(emoji);
    }

    @Override
    public void onDelete() {
	mEtInput.delete();
    }

    @Override
    public void onSoftKeyboardOpened(int keyboardHeightInPx) {
	mIsKeyboardVisible = true;
	hideEmojiPanel();
    }

    @Override
    public void onSoftKeyboardClosed() {
	mIsKeyboardVisible = false;
	if (mNeedHideEmoji) {
	    showEmojiPanel();
	}
    }

    private void showEmojiPanel() {
	mNeedHideEmoji = false;
	mLyEmoji.setVisibility(View.VISIBLE);
	mIbEmoji.setImageResource(R.drawable.compose_toolbar_keyboard_selector);
    }

    private void tryShowEmojiPanel() {
	if (mIsKeyboardVisible) {
	    TDevice.hideSoftKeyboard(this.getCurrentFocus());
	} else {
	    showEmojiPanel();
	}
    }

    private void tryHideEmojiPanel() {
	if (!mIsKeyboardVisible) {
	    TDevice.showSoftKeyboard(mEtInput);
	} else {
	    hideEmojiPanel();
	}
    }

    private void hideEmojiPanel() {
	if (mLyEmoji.getVisibility() == View.VISIBLE) {
	    mLyEmoji.setVisibility(View.GONE);
	    mIbEmoji.setImageResource(R.drawable.compose_toolbar_emoji_selector);
	}
    }

    @Override
    public void onBackPressed() {
	// TODO Auto-generated method stub
	if (mEtInput.getText().length() != 0) {
	    showConfirmExit();
	    return;
	}
	super.onBackPressed();

    }

    private void showConfirmExit() {
	CommonDialog dialog = DialogHelper.getPinterestDialogCancelable(this);
	dialog.setMessage("是否取消发送动态?");
	dialog.setNegativeButton(R.string.cancle, new OnClickListener() {

	    @Override
	    public void onClick(DialogInterface dialog, int which) {
		dialog.dismiss();
	    }
	});
	dialog.setPositiveButton(R.string.ok, new OnClickListener() {

	    @Override
	    public void onClick(DialogInterface dialog, int which) {
		dialog.dismiss();
		finish();
	    }
	});
	dialog.show();
    }

    private void handleSelectPicture() {
	final CommonDialog dialog = DialogHelper
		.getPinterestDialogCancelable(this);
	dialog.setTitle(R.string.choose_picture);
	dialog.setNegativeButton(R.string.cancle, null);
	dialog.setItemsWithoutChk(
		getResources().getStringArray(R.array.choose_picture),
		new OnItemClickListener() {

		    @Override
		    public void onItemClick(AdapterView<?> parent, View view,
			    int position, long id) {
			dialog.dismiss();
			goToSelectPicture(position);
		    }
		});
	dialog.show();
    }

    public static final int ACTION_TYPE_ALBUM = 0;
    public static final int ACTION_TYPE_PHOTO = 1;

    private void goToSelectPicture(int position) {
	switch (position) {
	case ACTION_TYPE_ALBUM:
	    Intent intent;
	    if (Build.VERSION.SDK_INT < 19) {
		intent = new Intent();
		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(Intent.createChooser(intent, "选择图片"),
			ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
	    } else {
		intent = new Intent(Intent.ACTION_PICK,
			Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		startActivityForResult(Intent.createChooser(intent, "选择图片"),
			ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
	    }
	    break;
	case ACTION_TYPE_PHOTO:
	    // 判断是否挂载了SD卡
	    String savePath = "";
	    String storageState = Environment.getExternalStorageState();
	    if (storageState.equals(Environment.MEDIA_MOUNTED)) {
		savePath = Environment.getExternalStorageDirectory()
			.getAbsolutePath() + "/oschina/Camera/";
		File savedir = new File(savePath);
		if (!savedir.exists()) {
		    savedir.mkdirs();
		}
	    }

	    // 没有挂载SD卡，无法保存文件
	    if (StringUtils.isEmpty(savePath)) {
		AppContext.showToastShort("无法保存照片，请检查SD卡是否挂载");
		return;
	    }

	    String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss")
		    .format(new Date());
	    String fileName = "osc_" + timeStamp + ".jpg";// 照片命名
	    File out = new File(savePath, fileName);
	    Uri uri = Uri.fromFile(out);

	    theLarge = savePath + fileName;// 该照片的绝对路径

	    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
	    startActivityForResult(intent,
		    ImageUtils.REQUEST_CODE_GETIMAGE_BYCAMERA);
	    break;
	default:
	    break;
	}
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
	    final Intent imageReturnIntent) {
	if (resultCode != Activity.RESULT_OK)
	    return;
	new Thread() {
	    private String selectedImagePath;

	    @Override
	    public void run() {
		Bitmap bitmap = null;

		if (requestCode == ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD) {
		    if (imageReturnIntent == null)
			return;
		    Uri selectedImageUri = imageReturnIntent.getData();
		    if (selectedImageUri != null) {
			selectedImagePath = ImageUtils.getImagePath(
				selectedImageUri, TeamNewActiveActivity.this);
		    }

		    if (selectedImagePath != null) {
			theLarge = selectedImagePath;
		    } else {
			bitmap = ImageUtils.loadPicasaImageFromGalley(
				selectedImageUri, TeamNewActiveActivity.this);
		    }

		    if (AppContext
			    .isMethodsCompat(android.os.Build.VERSION_CODES.ECLAIR_MR1)) {
			String imaName = FileUtil.getFileName(theLarge);
			if (imaName != null)
			    bitmap = ImageUtils.loadImgThumbnail(
				    TeamNewActiveActivity.this, imaName,
				    MediaStore.Images.Thumbnails.MICRO_KIND);
		    }
		    if (bitmap == null && !StringUtils.isEmpty(theLarge))
			bitmap = ImageUtils
				.loadImgThumbnail(theLarge, 100, 100);
		} else if (requestCode == ImageUtils.REQUEST_CODE_GETIMAGE_BYCAMERA) {
		    // 拍摄图片
		    if (bitmap == null && !StringUtils.isEmpty(theLarge)) {
			bitmap = ImageUtils
				.loadImgThumbnail(theLarge, 100, 100);
		    }
		}

		if (bitmap != null) {// 存放照片的文件夹
		    String savePath = Environment.getExternalStorageDirectory()
			    .getAbsolutePath() + "/OSChina/Camera/";
		    File savedir = new File(savePath);
		    if (!savedir.exists()) {
			savedir.mkdirs();
		    }

		    String largeFileName = FileUtil.getFileName(theLarge);
		    String largeFilePath = savePath + largeFileName;
		    // 判断是否已存在缩略图
		    if (largeFileName.startsWith("thumb_")
			    && new File(largeFilePath).exists()) {
			theThumbnail = largeFilePath;
			imgFile = new File(theThumbnail);
		    } else {
			// 生成上传的800宽度图片
			String thumbFileName = "thumb_" + largeFileName;
			theThumbnail = savePath + thumbFileName;
			if (new File(theThumbnail).exists()) {
			    imgFile = new File(theThumbnail);
			} else {
			    try {
				// 压缩上传的图片
				ImageUtils.createImageThumbnail(
					TeamNewActiveActivity.this, theLarge,
					theThumbnail, 800, 80);
				imgFile = new File(theThumbnail);
			    } catch (IOException e) {
				e.printStackTrace();
			    }
			}
		    }
		    // 保存动弹临时图片
		    // ((AppContext) getApplication()).setProperty(
		    // tempTweetImageKey, theThumbnail);

		    Message msg = new Message();
		    msg.what = 1;
		    msg.obj = bitmap;
		    handler.sendMessage(msg);
		}
	    };
	}.start();
    }

    private List<TeamMember> mTeamMemberList;

    private CommonDialog metionUserDialog;

    private void tryToShowMetionUser() {
	if (mTeamMemberList == null || mTeamMemberList.isEmpty()) {
	    OSChinaApi.getTeamMemberList(mTeam.getId(),
		    new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int arg0, Header[] arg1,
				byte[] arg2) {
			    // TODO Auto-generated method stub
			    TeamMemberList memberList = XmlUtils.toBean(
				    TeamMemberList.class, arg2);
			    
			    if (memberList != null) {
				mTeamMemberList = memberList.getList();
				showMetionUser();
			    } else {
				AppContext.showToast("获取团队成员失败");
			    }
			}

			@Override
			public void onFailure(int arg0, Header[] arg1,
				byte[] arg2, Throwable arg3) {
			    // TODO Auto-generated method stub
			    AppContext.showToast("获取团队成员失败");
			}

			@Override
			public void onStart() {
			    // TODO Auto-generated method stub
			    super.onStart();
			    showWaitDialog("正在获取团队成员...");
			}

			@Override
			public void onFinish() {
			    // TODO Auto-generated method stub
			    super.onFinish();
			    hideWaitDialog();
			}

		    });
	} else {
	    showMetionUser();
	}
    }

    private void showMetionUser() {
	if (mTeamMemberList == null || mTeamMemberList.isEmpty())
	    return;
	if (metionUserDialog == null) {
	    metionUserDialog = DialogHelper.getPinterestDialogCancelable(this);
	    metionUserDialog.setTitle("艾特团队成员");

	    final CharSequence[] toUsers = new CharSequence[mTeamMemberList
		    .size() + 1];
	    toUsers[0] = "全体成员(all)";
	    for (int i = 1; i < toUsers.length; i++) {
		toUsers[i] = mTeamMemberList.get(i - 1).getName();
	    }
	    metionUserDialog.setItems(toUsers, -1, new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view,
			int position, long id) {
		    // TODO Auto-generated method st
		    mEtInput.getText().insert(mEtInput.getSelectionStart(),
			    "@" + toUsers[position] + " ");
		    mEtInput.setSelection(mEtInput.length());
		    metionUserDialog.dismiss();
		}
	    });
	}

	metionUserDialog.show();
    }
}