package com.totsp.bookworm;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

import com.totsp.bookworm.model.Book;
import com.totsp.bookworm.util.AuthorsStringUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

public class BookForm extends TabActivity {

   private static final int SELECT_IMAGE = 0;

   private BookWormApplication application;

   private TabHost tabHost;

   private TextView bookEnterEditLabel;
   private ImageView bookCover;
   private TextView bookTitleCoverTab;
   private EditText bookTitleFormTab;
   private EditText bookSubTitle;
   private EditText bookIsbn10;
   private EditText bookIsbn13;
   private EditText bookAuthors;
   private EditText bookSubject;
   private DatePicker bookDatePub;
   private EditText bookPublisher;

   private Button saveButton;
   private Button selectCoverButton;
   private Button retrieveCoverButton;
   private Button generateCoverButton;

   // keep handle to AsyncTasks so cleanup in onPause can be done (else would just create new during usage)
   private RetrieveCoverImageTask RetrieveCoverImageTask;
   private GenerateCoverImageTask generateCoverImageTask;
   ///private SelectCoverImageTask selectCoverImageTask;
   private SaveBookTask saveBookTask;

   @Override
   public void onCreate(final Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      this.setContentView(R.layout.bookform);
      this.application = (BookWormApplication) this.getApplication();

      this.RetrieveCoverImageTask = null;
      this.generateCoverImageTask = null;
      ///this.selectCoverImageTask = null;
      this.saveBookTask = null;

      this.tabHost = this.getTabHost();
      this.tabHost.addTab(this.tabHost.newTabSpec("tab1").setIndicator(this.getString(R.string.menuEditBookDetails),
               this.getResources().getDrawable(android.R.drawable.ic_menu_edit)).setContent(R.id.bookformtab1));
      this.tabHost.addTab(this.tabHost.newTabSpec("tab2").setIndicator(this.getString(R.string.menuManageCoverImage),
               this.getResources().getDrawable(android.R.drawable.ic_menu_crop)).setContent(R.id.bookformtab2));
      this.tabHost.setCurrentTab(0);

      // make sure if user is ADDing a new book, and title/authors not set, they cannot go to Manage Cover Image tab
      // (we have to have a book first, before we can manage cover image)
      this.tabHost.setOnTabChangedListener(new OnTabChangeListener() {
         public void onTabChanged(final String tabName) {
            if (tabName.equals("tab2") && (BookForm.this.application.getSelectedBook() == null)) {
               Toast.makeText(
                        BookForm.this, BookForm.this.getString(R.string.msgMinimumSave), Toast.LENGTH_LONG).show();
               BookForm.this.tabHost.setCurrentTab(0);
            }
         }
      });

      this.bookEnterEditLabel = (TextView) this.findViewById(R.id.bookentereditlabel);
      this.bookCover = (ImageView) this.findViewById(R.id.bookcover);
      this.bookTitleFormTab = (EditText) this.findViewById(R.id.booktitleform);
      this.bookTitleCoverTab = (TextView) this.findViewById(R.id.booktitlecover);
      this.bookSubTitle = (EditText) this.findViewById(R.id.booksubtitle);
      this.bookIsbn10 = (EditText) this.findViewById(R.id.bookisbn10);
      this.bookIsbn13 = (EditText) this.findViewById(R.id.bookisbn13);
      this.bookAuthors = (EditText) this.findViewById(R.id.bookauthors);
      this.bookSubject = (EditText) this.findViewById(R.id.booksubject);
      this.bookDatePub = (DatePicker) this.findViewById(R.id.bookdatepub);
      this.bookPublisher = (EditText) this.findViewById(R.id.bookpublisher);

      this.saveButton = (Button) this.findViewById(R.id.bookformsavebutton);
      this.saveButton.setOnClickListener(new OnClickListener() {
         public void onClick(final View v) {
            BookForm.this.saveEdits();
         }
      });

      this.selectCoverButton = (Button) this.findViewById(R.id.bookformselectcoverbutton);
      this.selectCoverButton.setOnClickListener(new OnClickListener() {
         public void onClick(final View v) {
            try {
               BookForm.this.tabHost.setCurrentTab(0);
               BookForm.this.startActivityForResult(new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI), BookForm.SELECT_IMAGE);
            } catch (ActivityNotFoundException e) {
               Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgNoActivityGalleryError),
                        Toast.LENGTH_LONG).show();
            }
         }
      });

      this.retrieveCoverButton = (Button) this.findViewById(R.id.bookformretrievecoverbutton);
      this.retrieveCoverButton.setOnClickListener(new OnClickListener() {
         public void onClick(final View v) {
            BookForm.this.RetrieveCoverImageTask = new RetrieveCoverImageTask();
            BookForm.this.RetrieveCoverImageTask.execute(BookForm.this.application.getSelectedBook());
         }
      });

      this.generateCoverButton = (Button) this.findViewById(R.id.bookformgeneratecoverbutton);
      this.generateCoverButton.setOnClickListener(new OnClickListener() {
         public void onClick(final View v) {
            BookForm.this.generateCoverImageTask = new GenerateCoverImageTask();
            BookForm.this.generateCoverImageTask.execute(BookForm.this.application.getSelectedBook());
         }
      });

      // if Application selectedBook is present, EDIT mode, else ADD mode
      if (this.application.getSelectedBook() != null) {
         this.bookEnterEditLabel.setText(this.getString(R.string.menuEditBook));
         this.setExistingViewData();
      } else {
         this.bookEnterEditLabel.setText(this.getString(R.string.menuAddBook));
      }
   }

   @Override
   public void onStart() {
      super.onStart();
   }

   @Override
   public void onPause() {
      this.bookTitleFormTab = null;
      if ((this.generateCoverImageTask != null) && this.generateCoverImageTask.dialog.isShowing()) {
         this.generateCoverImageTask.dialog.dismiss();
      }
      if ((this.RetrieveCoverImageTask != null) && this.RetrieveCoverImageTask.dialog.isShowing()) {
         this.RetrieveCoverImageTask.dialog.dismiss();
      }      
      if ((this.saveBookTask != null) && this.saveBookTask.dialog.isShowing()) {
         this.saveBookTask.dialog.dismiss();
      }
      super.onPause();
   }

   @Override
   public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      if (requestCode == BookForm.SELECT_IMAGE) {
         if (resultCode == Activity.RESULT_OK) {
            // intentionally do NOT use an AsyncTask (confusing for UI, returns onActResult too fast)
            Uri selectedImageUri = data.getData();
            InputStream is = null;
            try {
               is = BookForm.this.getContentResolver().openInputStream(selectedImageUri);
               Bitmap bitmap = BitmapFactory.decodeStream(is);
               Book book = BookForm.this.application.getSelectedBook();
               if ((bitmap != null) && (book != null)) {
                  BookForm.this.application.getDataImageHelper().storeBitmap(bitmap, book.title, book.id);
               }
            } catch (FileNotFoundException e) {
               Log.e(Constants.LOG_TAG, e.getMessage(), e);
            } finally {
               if (is != null) {
                  try {
                     is.close();
                  } catch (IOException e) {
                     // swallow
                  }
               }
            }

            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgBookUpdated), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(BookForm.this, BookDetail.class);
            BookForm.this.startActivity(intent);
         }
      }
   }

   @Override
   protected void onRestoreInstanceState(final Bundle savedInstanceState) {
      super.onRestoreInstanceState(savedInstanceState);
      if (this.application.getSelectedBook() == null) {
         long bookId = savedInstanceState.getLong(Constants.BOOK_ID, 0L);
         if (bookId > 0) {
            this.application.establishSelectedBook(bookId);
            this.setExistingViewData();
         }
      }
   }

   @Override
   protected void onSaveInstanceState(final Bundle saveState) {
      if (this.application.getSelectedBook() != null) {
         saveState.putLong(Constants.BOOK_ID, this.application.getSelectedBook().id);
      }
      super.onSaveInstanceState(saveState);
   }

   private void setExistingViewData() {
      Book book = this.application.getSelectedBook();
      Bitmap coverImage = this.application.getDataImageHelper().retrieveBitmap(book.title, book.id, false);
      if (coverImage != null) {
         this.bookCover.setImageBitmap(coverImage);
      } else {
         this.bookCover.setImageResource(R.drawable.book_cover_missing);
      }

      this.bookTitleFormTab.setText(book.title);
      this.bookTitleCoverTab.setText(book.title);
      this.bookSubTitle.setText(book.subTitle);
      this.bookIsbn10.setText(book.isbn10);
      this.bookIsbn13.setText(book.isbn13);
      this.bookAuthors.setText(AuthorsStringUtil.contractAuthors(book.authors));
      this.bookSubject.setText(book.subject);
      this.bookPublisher.setText(book.publisher);

      Calendar cal = Calendar.getInstance();
      cal.setTimeInMillis(book.datePubStamp);
      this.bookDatePub.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
   }

   private void saveEdits() {
      // establish newBook
      Book newBook = new Book();
      newBook.title = (this.bookTitleFormTab.getText().toString());
      newBook.subTitle = (this.bookSubTitle.getText().toString());
      newBook.isbn10 = (this.bookIsbn10.getText().toString());
      newBook.isbn13 = (this.bookIsbn13.getText().toString());
      newBook.authors = (AuthorsStringUtil.expandAuthors(this.bookAuthors.getText().toString()));
      newBook.subject = (this.bookSubject.getText().toString());
      newBook.publisher = (this.bookPublisher.getText().toString());

      Calendar cal = Calendar.getInstance();
      cal.set(Calendar.YEAR, this.bookDatePub.getYear());
      cal.set(Calendar.MONTH, this.bookDatePub.getMonth());
      cal.set(Calendar.DAY_OF_MONTH, this.bookDatePub.getDayOfMonth());
      newBook.datePubStamp = cal.getTimeInMillis();

      // save settings from existing book, if present (if we are editing)
      Book book = this.application.getSelectedBook();
      if (book != null) {
         newBook.id = (book.id);

         // NOTE - properties not yet editable (will be in future)        
         newBook.description = (book.description);
         newBook.format = (book.format);

         // properties editable on display page and not on edit page
         newBook.rating = (book.rating);
         newBook.read = (book.read);

         // rename the cover images too, if title changes
         if (book.title != newBook.title) {
            this.application.getDataImageHelper().renameBitmapSourceFile(book.title, newBook.title, book.id);
         }
      }

      this.saveBookTask = new SaveBookTask();
      this.saveBookTask.execute(newBook);
   }

   //
   // AsyncTasks
   //
   private class SaveBookTask extends AsyncTask<Book, Void, Boolean> {
      private final ProgressDialog dialog = new ProgressDialog(BookForm.this);

      private boolean newBook;

      protected void onPreExecute() {
         this.dialog.setMessage(BookForm.this.getString(R.string.msgSavingBookInfo));
         this.dialog.show();
      }

      protected Boolean doInBackground(final Book... args) {
         Book book = args[0];
         if ((book != null) && (book.id > 0)) {
            BookForm.this.application.getDataHelper().updateBook(book);
            BookForm.this.application.establishSelectedBook(book.id);
            return true;
         } else if ((book != null) && (book.id == 0)) {
            this.newBook = true;
            long bookId = BookForm.this.application.getDataHelper().insertBook(book);
            BookForm.this.application.establishSelectedBook(bookId);
            // also auto store generated cover with new form based book insert
            Bitmap generatedCover = BookForm.this.application.getDataImageHelper().createCoverImage(book.title);
            BookForm.this.application.getDataImageHelper().storeBitmap(generatedCover, book.title, bookId);
            return true;
         }
         return false;
      }

      protected void onPostExecute(final Boolean b) {
         if (this.dialog.isShowing()) {
            this.dialog.dismiss();
         }
         if (!b) {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgBookSaveError),
                     Toast.LENGTH_LONG).show();
         } else {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgBookSaved), Toast.LENGTH_SHORT).show();
            if (!this.newBook) {
               Intent intent = new Intent(BookForm.this, BookDetail.class);
               intent.putExtra("RELOAD_AFTER_EDIT", true);
               BookForm.this.startActivity(intent);
            } else {
               BookForm.this.setExistingViewData();
            }
         }
      }
   }

   private class RetrieveCoverImageTask extends AsyncTask<Book, Void, Boolean> {
      private final ProgressDialog dialog = new ProgressDialog(BookForm.this);

      protected void onPreExecute() {
         this.dialog.setMessage(BookForm.this.getString(R.string.msgRetrieveCoverImage));
         this.dialog.show();
      }

      protected Boolean doInBackground(final Book... args) {
         Book book = args[0];
         if ((book != null) && (book.id > 0)) {
            BookForm.this.application.getDataImageHelper().resetCoverImage(BookForm.this.application.getDataHelper(),
                     book);
            return true;
         }
         return false;
      }

      protected void onPostExecute(final Boolean b) {
         if (this.dialog.isShowing()) {
            this.dialog.dismiss();
         }
         if (!b) {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgRetrieveCoverImageError),
                     Toast.LENGTH_LONG).show();
         } else {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgBookUpdated), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(BookForm.this, BookDetail.class);
            BookForm.this.startActivity(intent);
         }
      }
   }

   private class GenerateCoverImageTask extends AsyncTask<Book, Void, Boolean> {
      private final ProgressDialog dialog = new ProgressDialog(BookForm.this);

      protected void onPreExecute() {
         this.dialog.setMessage(BookForm.this.getString(R.string.msgGenerateCoverImage));
         this.dialog.show();
      }

      protected Boolean doInBackground(final Book... args) {
         Book book = args[0];
         if ((book != null) && (book.id > 0)) {
            Bitmap generatedCover = BookForm.this.application.getDataImageHelper().createCoverImage(book.title);
            BookForm.this.application.getDataImageHelper().storeBitmap(generatedCover, book.title, book.id);
            return true;
         }
         return false;
      }

      protected void onPostExecute(final Boolean b) {
         if (this.dialog.isShowing()) {
            this.dialog.dismiss();
         }
         if (!b) {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgGenerateCoverImageError),
                     Toast.LENGTH_LONG).show();
         } else {
            Toast.makeText(BookForm.this, BookForm.this.getString(R.string.msgBookUpdated), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(BookForm.this, BookDetail.class);
            BookForm.this.startActivity(intent);
         }
      }
   }
}