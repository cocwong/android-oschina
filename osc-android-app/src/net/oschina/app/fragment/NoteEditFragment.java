package net.oschina.app.fragment;

import java.text.SimpleDateFormat;
import java.util.Date;

import net.oschina.app.R;
import net.oschina.app.base.BaseFragment;
import net.oschina.app.bean.NotebookData;
import net.oschina.app.db.NoteDatabase;
import net.oschina.app.ui.SimpleBackActivity;
import net.oschina.app.util.KJAnimations;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * 便签编辑界面
 * 
 * @author kymjs(kymjs123@gmail.com)
 * 
 */
public class NoteEditFragment extends BaseFragment implements OnTouchListener {
    @InjectView(R.id.note_detail_edit)
    EditText mEtContent;
    @InjectView(R.id.note_detail_root_layout)
    RelativeLayout mLayoutRoot;
    @InjectView(R.id.note_detail_menu)
    RelativeLayout mLayoutMenu;
    @InjectView(R.id.note_detail_img_button)
    ImageView mImgMenu;

    @InjectView(R.id.note_detail_img_green)
    ImageView mImgGreen;
    @InjectView(R.id.note_detail_img_blue)
    ImageView mImgBlue;
    @InjectView(R.id.note_detail_img_purple)
    ImageView mImgPurple;
    @InjectView(R.id.note_detail_img_yellow)
    ImageView mImgYellow;
    @InjectView(R.id.note_detail_img_red)
    ImageView mImgRed;

    private NotebookData editData;
    private NoteDatabase noteDb;
    public static final String NOTE_KEY = "notebook_key";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.fragment_note_detail,
                container, false);
        ButterKnife.inject(this, rootView);
        initData();
        initView(rootView);
        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.note_detail_img_green:
            editData.setColor(0xffbcf7aa);
            break;
        case R.id.note_detail_img_blue:
            editData.setColor(0xffaadff7);
            break;
        case R.id.note_detail_img_purple:
            editData.setColor(0xffc045fc);
            break;
        case R.id.note_detail_img_yellow:
            editData.setColor(0xfff7f4aa);
            break;
        case R.id.note_detail_img_red:
            editData.setColor(0xfffca9a9);
            break;
        }
        mLayoutRoot.setBackgroundColor(editData.getColor());
        closeMenu();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (mLayoutMenu.getVisibility() == View.GONE) {
                openMenu();
            } else {
                closeMenu();
            }
        }
        return true;
    }

    @Override
    public void initData() {
        noteDb = new NoteDatabase(getActivity());
        if (editData == null) {
            editData = new NotebookData();
        }
    }

    @Override
    public void initView(View view) {
        mImgGreen.setOnClickListener(this);
        mImgBlue.setOnClickListener(this);
        mImgPurple.setOnClickListener(this);
        mImgYellow.setOnClickListener(this);
        mImgRed.setOnClickListener(this);
        mLayoutRoot.setBackgroundColor(editData.getColor());
        mEtContent.setText(editData.getContent());

        mImgMenu.setOnTouchListener(this);
        mLayoutMenu.setOnTouchListener(this);
    }

    /**
     * 切换便签颜色的菜单
     */
    private void openMenu() {
        KJAnimations.openAnimation(mLayoutMenu, mImgMenu, 500);
    }

    /**
     * 切换便签颜色的菜单
     */
    private void closeMenu() {
        KJAnimations.closeAnimation(mLayoutMenu, mImgMenu, 500);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle bundle = getActivity().getIntent().getBundleExtra(
                SimpleBackActivity.BUNDLE_KEY_ARGS);
        if (bundle != null) {
            editData = (NotebookData) bundle.getSerializable(NOTE_KEY);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.pub_tweet_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.public_menu_send:
            save();
            getActivity().finish();
            break;
        }
        return true;
    }

    /**
     * 保存已编辑内容到数据库
     */
    private void save() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM年dd月");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        editData.setStar(false);
        editData.setContent(mEtContent.getText().toString());
        editData.setDate(dateFormat.format(date));
        editData.setTime(timeFormat.format(date));
        noteDb.save(editData);
    }
}