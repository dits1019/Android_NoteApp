package com.example.noteapp.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.noteapp.R;
import com.example.noteapp.adapters.NotesAdapter;
import com.example.noteapp.database.NotesDatabase;
import com.example.noteapp.entities.Note;
import com.example.noteapp.listeners.NotesListeners;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NotesListeners {

    //이 요청 코드는 새 노트를 추가하는 데 사용됨
    public static final int REQUEST_CODE_ADD_NOTE = 1;
    //이 요청 코드는 노트를 업데이트 하는데 사용됨
    public static final int REQUEST_CODE_UPDATE_NOTE = 2;
    //이 요청 코드는 모든 노트를 표시하는 데 사용됨
    public static final int REQUEST_CODE_SHOW_NOTES = 3;

    public static final int REQUEST_CODE_SELECT_IMAGE = 4;

    public static final int REQUEST_CODE_STORAGE_PERMISSION = 5;

    private RecyclerView notesRecyclerView;
    private List<Note> noteList;
    private NotesAdapter notesAdapter;

    private int noteClickedPosition = -1;

    private AlertDialog dialogAddURL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageAddNoteMain = findViewById(R.id.imageAddNoteMain);
        imageAddNoteMain.setOnClickListener(view -> {
            //결과에 대해 "CreateNoteActivity"를 시작했기 때문에 "CreateNoteActivity"에서
            //노트를 추가한 후 노트 목록을 업데이트하려면 "onActivityResult" 방법으로 결과를
            //처리해야 합니다.

            startActivityForResult(
                    new Intent(getApplicationContext(), CreateNoteActivity.class),
                    REQUEST_CODE_ADD_NOTE
            );
        });

        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        notesRecyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        );

        noteList = new ArrayList<>();
        notesAdapter = new NotesAdapter(noteList, this);
        notesRecyclerView.setAdapter(notesAdapter);

        //이 getNotes() 메서드는 활동의 onCreate() 메서드에서 호출됩니다.
        //이것은 응용 프로그램이 이제 막 시작되었고 데이터베이스의 모든 노트를 표시해야 한다는 것을 의미하며,
        //그것이 우리가 RESSURE_CODE_SHOW_NOTES를 그 방법에 전달하는 이유입니다.

        //여기서 요청 코드는 RESSURE_CODE_SHOW_NOTES이다.
        //이는 데이터베이스의 모든 노트를 표시하므로,
        //매개 변수가 NoteDelete이므로 'false'를 전달
        getNotes(REQUEST_CODE_SHOW_NOTES, false);

        EditText inputSearch = findViewById(R.id.inputSearch);
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                notesAdapter.cancelTimer();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(noteList.size() != 0){
                    notesAdapter.searchNotes(s.toString());
                }
            }
        });

        findViewById(R.id.imageAddNote).setOnClickListener(v -> startActivityForResult(
                new Intent(getApplicationContext(), CreateNoteActivity.class),
                REQUEST_CODE_ADD_NOTE
        ));

        findViewById(R.id.imageAddImage).setOnClickListener(v -> {
            if(ContextCompat.checkSelfPermission(
                    getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String [] {Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE_PERMISSION
                );
            }else{
                selectImage();
            }
        });

        findViewById(R.id.imageAddWebLink).setOnClickListener(v -> {
            showAddURLDialog();
        });

    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.length > 0){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                selectImage();
            }else {
                Toast.makeText(this, "Permission Denied ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getPathFromUri(Uri contentUri){
        String filePath;
        Cursor cursor = getContentResolver()
                .query(contentUri, null, null, null, null);
        if(cursor == null){
            filePath = contentUri.getPath();
        }else{
            cursor.moveToFirst();
            int index = cursor.getColumnIndex("_data");
            filePath = cursor.getString(index);
            cursor.close();
        }
        return filePath;
    }

    @Override
    public void onNoteClicked(Note note, int position) {
        noteClickedPosition = position;
        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
        intent.putExtra("isViewOrUpdate", true);
        intent.putExtra("note", note);
        startActivityForResult(intent, REQUEST_CODE_UPDATE_NOTE);
    }

    //노트를 저장하는 데 비동기 태스크가 필요한 것처럼 데이터베이스에서도 노트를 가져올 수 있음

    private void getNotes(final int requestCode, final boolean isNoteDeleted){ //요청 코드를 메서드 매개 변수로 가져오기, 삭제 버튼이 뜰지 안 뜰지

        @SuppressLint("StaticFieldLeak")
        class GetNotesTask extends AsyncTask<Void, Void, List<Note>> {
            @Override
            protected List<Note> doInBackground(Void... voids){
                return NotesDatabase
                        .getDatabase(getApplicationContext())
                        .noteDao().getAllNotes();
            }

            /*노트 목록이 비어 있으면 앱이 글로벌 변수로 선언되었기 때문에 앱이 방금 시작되었음을 의미하며,
            이 경우 데이터베이스의 모든 노트를 이 노트 목록에 추가하고 어댑터에게 새 데이터 세트에 대해 알립니다.
            다른 경우, 노트 목록이 비어 있지 않으면 데이터베이스에서 노트가 이미 로드되었음을 의미하므로 노트 목록에
            최신 노트만 추가하고 새 노트에 대해 어댑터에게 알립니다.
            마지막으로 RecyclerView를 맨 위로 스크롤했습니다.*/

            @Override
            protected void onPostExecute(List<Note> notes){
                super.onPostExecute(notes);
                //여기서 요청 코드는 RESSURE_CODE_SHOW_NOTES이므로 데이터베이스의 모든 노트를 noteList에 추가하고
                //새 데이터 세트에 대해 어댑터에게 알립니다.
                if(requestCode == REQUEST_CODE_SHOW_NOTES){
                    noteList.addAll(notes);
                    notesAdapter.notifyDataSetChanged();
                }
                //여기서 요청 코드는 RESSURE_COD_AND_NOTE이기 때문에 noteList에
                //데이터베이스의 첫 번째 노트(새로 추가된 노트)만 추가하고 새로 삽입한 항목에 대해 어댑터에게 알리고
                //RecyclerView를 상단으로 스크롤합니다.
                else if(requestCode == REQUEST_CODE_ADD_NOTE){
                    noteList.add(0, notes.get(0));
                    notesAdapter.notifyItemInserted(0);
                    notesRecyclerView.smoothScrollToPosition(0);
                }
                //여기서 요청 코드는 RESSURE_CODE_UPDATE_NOTE이므로 클릭된 위치에서 노트를 제거하고
                //데이터베이스에서 동일한 위치에서 최신 업데이트 노트를 추가한 후
                //해당 위치에서 변경된 항목에 대해 어댑터에 알립니다.

                //요청 코드가 RESSURE_CODE_UPDATE_NOTE인 경우, 먼저 목록에서 노트를 제거
                //그런 다음 노트의 삭제 여부를 확인
                //노트가 삭제되면 제거된 항목에 대해 어댑터에 알림
                //노트가 삭제되지 않은 경우 업데이트되어야 하므로 새로 업데이트된 노트를 제거한 동일한 위치에 추가하고 변경된 항목에 대해 알림
                else if(requestCode == REQUEST_CODE_UPDATE_NOTE){
                    noteList.remove(noteClickedPosition);

                    if(isNoteDeleted){
                        notesAdapter.notifyItemRemoved(noteClickedPosition);
                    }else{
                        noteList.add(noteClickedPosition, notes.get(noteClickedPosition));
                        notesAdapter.notifyItemChanged(noteClickedPosition);
                    }

                }
            }

        }

        new GetNotesTask().execute();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @NonNull Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_ADD_NOTE && resultCode == RESULT_OK){
            //이 getNotes() 메서드는 onActivityResult() 작업 메서드에서 호출되며
            //현재 요청 코드는 추가 참고용이고 결과는 RESSURENT_OK이다.
            //이것은 노트 만들기 활동에서 새 노트가 추가되고 그 결과가 이 활동으로
            //다시 전송된다는 것을 의미하므로 우리는 RESSURE_CODE_ADD_NOTE를 해당 메서드에 전달

            //여기서 요청 코드는 RESSURE_CODE_AD_NOTE이며,
            //이는 데이터베이스에 새 노트를 추가하였음을 의미하므로,
            //매개 변수가 NoteDelete이므로 'false'를 전달
            getNotes(REQUEST_CODE_ADD_NOTE, false);
        }else if(requestCode == REQUEST_CODE_UPDATE_NOTE && resultCode == RESULT_OK){
            if(data != null) {
                //이 getNotes() 메서드는 onActivityResult() 작업 메소드에서 호출되며
                //현재 요청 코드는 업데이트 노트용이고 결과는 RESSUEST_OK이다.
                //즉, 이미 사용 가능한 노트가 CreateNote 활동에서 업데이트되었으며
                //결과가 이 활동으로 다시 전송되므로 RESSUTE_CODE_UPDATE_NOTE를 메서드로 전달

                //여기서 요청 코드는 RESSUTE_CODE_UPDATE_NOTE이다.
                //즉, 데이터베이스에서 이미 사용 가능한 노트를 업데이트하고 있으며,
                //노트가 삭제되었을 때 매개 변수가 노트 삭제됨으로 인해 노트 생성 활동에서 값을 전달하며,
                //해당 노트가 삭제되었는지 여부를 "IsNote Deleted" 키와 함께 사용
                getNotes(REQUEST_CODE_UPDATE_NOTE, data.getBooleanExtra("isNoteDeleted", false));
            }
        }
        //이미지 추가 퀵메뉴
        else if(requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK){
            if(data != null){
                Uri selectedImageUri = data.getData();
                if(selectedImageUri != null){
                    try{
                        String selectedImagePath = getPathFromUri(selectedImageUri);
                        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                        intent.putExtra("isFromQuickActions", true);
                        intent.putExtra("quickActionType", "image");
                        intent.putExtra("imagePath", selectedImagePath);
                        startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
                    }catch (Exception e){
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    //URL 추가 퀵메뉴
    private void showAddURLDialog(){
        if(dialogAddURL == null){
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            View view = LayoutInflater.from(this).inflate(
                    R.layout.layout_add_url,
                    (ViewGroup)findViewById(R.id.layoutAddUrlContainer)
            );
            builder.setView(view);

            dialogAddURL = builder.create();
            if(dialogAddURL.getWindow() != null){
                dialogAddURL.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            }

            final EditText inputURL = view.findViewById(R.id.inputURL);
            inputURL.requestFocus();

            //추가버튼
            view.findViewById(R.id.textAdd).setOnClickListener(view1 -> {
                if(inputURL.getText().toString().trim().isEmpty()){ // URL입력창이 비었을 때
                    Toast.makeText(MainActivity.this, "Enter URL", Toast.LENGTH_SHORT).show();
                }else if(!Patterns.WEB_URL.matcher(inputURL.getText().toString()).matches()){ //URL 형식이 아닐 경우
                    Toast.makeText(MainActivity.this, "Enter valid URL", Toast.LENGTH_SHORT).show();
                }else{
                    dialogAddURL.dismiss();
                    Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                    intent.putExtra("isFromQuickActions", true);
                    intent.putExtra("quickActionType", "URL");
                    intent.putExtra("URL", inputURL.getText().toString());
                    startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
                }
            });

            //취소버튼
            view.findViewById(R.id.textCancel).setOnClickListener(view12 -> {
                dialogAddURL.dismiss();
            });

        }
        dialogAddURL.show();
    }

}