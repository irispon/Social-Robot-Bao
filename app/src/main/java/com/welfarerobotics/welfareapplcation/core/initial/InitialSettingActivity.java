package com.welfarerobotics.welfareapplcation.core.initial;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.kinda.alert.KAlertDialog;
import com.welfarerobotics.welfareapplcation.R;
import com.welfarerobotics.welfareapplcation.core.base.BaseActivity;
import com.welfarerobotics.welfareapplcation.core.main.MainActivity;
import com.welfarerobotics.welfareapplcation.entity.Server;
import com.welfarerobotics.welfareapplcation.entity.User;
import com.welfarerobotics.welfareapplcation.entity.cache.ServerCache;
import com.welfarerobotics.welfareapplcation.entity.cache.UserCache;
import com.welfarerobotics.welfareapplcation.util.DeviceId;
import com.welfarerobotics.welfareapplcation.util.NetworkUtil;
import com.welfarerobotics.welfareapplcation.util.ToastType;
import com.welfarerobotics.welfareapplcation.util.data_util.FirebaseHelper;
import com.welfarerobotics.welfareapplcation.util.data_util.Preference;
import es.dmoral.toasty.Toasty;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class InitialSettingActivity extends BaseActivity {
    private ArrayList<ImageView> iv_UserPhotos;
    private String mCurrentPhotoPath;
    private Uri photoURI;
    private FirebaseStorage storage;
    private ArrayList<String> photos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_init_setting);

        storage = FirebaseStorage.getInstance();
        Button nextButton = findViewById(R.id.initial_setting_next_button);
        iv_UserPhotos = new ArrayList<>();
        iv_UserPhotos.add((ImageView) findViewById(R.id.user_image_0));
        iv_UserPhotos.add((ImageView) findViewById(R.id.user_image_1));
        iv_UserPhotos.add((ImageView) findViewById(R.id.user_image_2));
        iv_UserPhotos.get(0).setOnClickListener(view -> captureImage(0));
        iv_UserPhotos.get(1).setOnClickListener(view -> captureImage(1));
        iv_UserPhotos.get(2).setOnClickListener(view -> captureImage(2));

        NetworkUtil.wifiSafe(this); // 네트워크 체크

        nextButton.setOnClickListener(v -> {
            if (photos.size() < 3) {
                showToast("사용자의 사진 3장을 모두 업로드 해주세요.\n사진은 얼굴인식 기능에 사용됩니다.", ToastType.error);
            } else {

                FirebaseHelper.get().connect(FirebaseDatabase.getInstance().getReference("server"), snapshot -> {
                    Server server = snapshot.getValue(Server.class);
                    ServerCache.setInstance(server);

                    String id = DeviceId.getInstance(this).getUUID();
                    User model = new User();
                    model.setId(id);

                    EditText nameEditText = findViewById(R.id.user_name);
                    EditText addressEditText = findViewById(R.id.user_address);

                    if (nameEditText.getText() != null &&
                            !nameEditText.getText().toString().trim().equals("") &&
                            !nameEditText.getText().toString().replaceAll(" ", "").equals("") &&
                            addressEditText.getText() != null &&
                            !addressEditText.getText().toString().trim().equals("") &&
                            !addressEditText.getText().toString().replaceAll(" ", "").equals("")) {

                        model.setName(nameEditText.getText().toString());
                        model.setLocation(addressEditText.getText().toString());
                        model.setPhoto(photos);
                        UserCache.setInstance(model);
                        // 싱글톤 업로드

                        FirebaseDatabase.getInstance()
                                .getReference("user")
                                .child(id)
                                .setValue(model);

                        Preference.get(this).setBoolean("isFirst", false);
                        startActivity(new Intent(InitialSettingActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toasty.error(this, "사용자 이름과 지역정보(시/군/구 단위까지)를 반드시 입력해주세요", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void captureImage(int rqCode) {
        int permissionCheck = ContextCompat.checkSelfPermission(InitialSettingActivity.this, Manifest.permission.CAMERA);
        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            //권한없음
            ActivityCompat.requestPermissions(InitialSettingActivity.this, new String[]{Manifest.permission.CAMERA
                    , Manifest.permission.WRITE_EXTERNAL_STORAGE}, rqCode);
            //ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},0);
        } else {
            //권한있음
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, rqCode);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        if (grantResult[0] == 0) {
            showToast("권한이 승인되었습니다", ToastType.success);
            doTakePhotoAction(requestCode);
        } else {
            //권한이 거절된경우
            Toast.makeText(this, "카메라권한이 거절되었습니다." +
                    " 카메라를 이용하려면 권한을 승낙하여야합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    //카메라에서 사진촬영
    private void doTakePhotoAction(int rqCode)// 카메라 찰영후 이미지가져오기
    {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = createImageFile();
                if (photoFile != null) {
                    // getURLForFile의 두번째 인자는 Manifest provider의 authorites와 일치해야함
                    //photoURI : fill://로 시작, FileProvider(Content Provider 하위)sms content:// 로시작
                    //누가(7.0)이상부터는 file://로 시작되는 Uri의 값을 주고받기 불가능
                    photoURI = FileProvider.getUriForFile(this, "패키지명", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, rqCode);
                }
            } else {
                Toast.makeText(InitialSettingActivity.this, "외장메모리 미 지원", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private File createImageFile() {
        //이미지 파일 이름 생성
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(new Date());
        String imageFileName = timeStamp + ".jpg";
        File storageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/pathvalue/" + imageFileName);

        //Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = storageDir.getAbsolutePath();
        return storageDir;
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final Bundle extras = data.getExtras();
        assert extras == null;
        //Bitmap photo = ImageUtil.cropCenter(extras.getParcelable("data")); centor crop기능 (extras.getParcelable("data"));
        Bitmap photo = extras.getParcelable("data");
        iv_UserPhotos.get(requestCode).setImageBitmap(photo);
        KAlertDialog pDialog = new KAlertDialog(this, KAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        pDialog.setTitleText("사진 업로드 중...");
        pDialog.setCancelable(false);
        pDialog.show();

        NetworkUtil.wifiSafe(this); // 네트워크 체크

        StorageReference storageRef = storage.getReference()
                .child("userPhoto")
                .child(DeviceId.getInstance(this).getUUID())
                .child("userPhoto" + requestCode);

        iv_UserPhotos.get(requestCode).setDrawingCacheEnabled(true);
        iv_UserPhotos.get(requestCode).buildDrawingCache();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Objects.requireNonNull(photo).compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] datas = baos.toByteArray();

        UploadTask uploadTask = storageRef.putBytes(datas);
        uploadTask.addOnFailureListener(exception -> {
            pDialog.dismissWithAnimation();
            showToast("사진 업로드에 실패하였습니다. 연결상태를 확인해주세요.", ToastType.error);
            startActivity(new Intent(this, InitialWifiActivity.class));
        }).addOnSuccessListener(taskSnapshot -> {
            pDialog.changeAlertType(KAlertDialog.SUCCESS_TYPE);
            pDialog.setTitleText("사진 업로드에 성공했습니다.");
            pDialog.confirmButtonColor(R.color.confirm_button);
            pDialog.setConfirmText("확인");
            pDialog.setConfirmClickListener(KAlertDialog::dismissWithAnimation);
            //이미지 URI을 RealTime Database에 upload
            photos.add(taskSnapshot.getDownloadUrl().toString());
        });
    }
}